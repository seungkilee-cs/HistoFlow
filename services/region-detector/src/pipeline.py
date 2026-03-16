"""Pipeline — orchestrates the full region-detection analysis for one slide.

Steps
-----
1. Parse the DZI descriptor to learn the tile grid dimensions.
2. List all tiles at the requested zoom level.
3. For each tile:
   a. Download the image from MinIO.
   b. Run tissue detection (skip if background).
   c. Embed tissue tiles with DINOv2.
   d. Classify each embedding with the sklearn head.
4. Aggregate tile-level results into a slide-level summary.
5. Generate a heatmap overlay image and upload it to MinIO.
6. Return the full result (tile predictions + summary + heatmap path).
"""

from __future__ import annotations

import time
from dataclasses import dataclass, asdict
from typing import Any, Callable, Dict, List, Optional

import numpy as np

from .classifier import Classifier
from .config import settings
from .embedder import Embedder
from .geometry import DZIShape, max_dzi_level, tile_rect_in_fullres
from .heatmap import generate_heatmap, heatmap_to_png_bytes
from .minio_io import (
    TileRef,
    download_tile_image,
    list_available_tile_levels,
    list_tiles_at_level,
    parse_dzi,
    upload_bytes,
)
from .tile_levels import select_analysis_level
from .tissue_detector import TissueResult, detect_tissue


# ── Result data classes ───────────────────────────────────────────────────────


@dataclass
class TilePrediction:
    tile_x: int
    tile_y: int
    tile_level: int
    pixel_x: int
    pixel_y: int
    width: int
    height: int
    is_tissue: bool
    tissue_ratio: float
    tumor_probability: float
    label: str


@dataclass
class SlideSummary:
    total_tiles: int
    tissue_tiles: int
    skipped_tiles: int
    flagged_tiles: int  # tiles exceeding threshold
    tumor_area_percentage: float
    aggregate_score: float
    max_score: float
    aggregation_method: str
    threshold: float


@dataclass
class AnalysisResult:
    image_id: str
    tile_level: int
    dzi: Dict[str, Any]
    summary: SlideSummary
    tile_predictions: List[TilePrediction]
    heatmap_key: str  # MinIO object key
    timings: Dict[str, float]


# ── Progress callback ────────────────────────────────────────────────────────

ProgressCallback = Optional[Callable[[int, int, str, int | None], None]]


# ── Pipeline ──────────────────────────────────────────────────────────────────


def run_analysis(
    image_id: str,
    tile_level: int | None = None,
    threshold: float | None = None,
    tissue_threshold: float | None = None,
    batch_size: int = 16,
    progress_cb: ProgressCallback = None,
) -> AnalysisResult:
    """Run the full region-detection pipeline for *image_id*.

    Parameters
    ----------
    image_id:
        The image / dataset identifier in MinIO (the top-level prefix).
    tile_level:
        DZI pyramid level to analyse.  Defaults to
        ``settings.DEFAULT_TILE_LEVEL``.
    threshold:
        Classification threshold for "Tumor" label.
    tissue_threshold:
        Minimum tissue ratio so a tile is analysed.
    batch_size:
        Number of tiles to embed at once (GPU batch).
    progress_cb:
        Optional ``(processed, total, message)`` callback for status updates.
    """
    requested_tile_level = tile_level if tile_level is not None else settings.DEFAULT_TILE_LEVEL
    threshold = threshold if threshold is not None else settings.CLASSIFICATION_THRESHOLD
    tissue_thresh = (
        tissue_threshold if tissue_threshold is not None else settings.TISSUE_THRESHOLD
    )

    timings: Dict[str, float] = {}

    # ── 1. Parse DZI ──────────────────────────────────────────────────
    t0 = time.perf_counter()
    dzi = parse_dzi(image_id)
    timings["parse_dzi_s"] = round(time.perf_counter() - t0, 3)
    _report(progress_cb, 0, 0, "Parsed DZI descriptor")

    # ── 2. List tiles at the chosen level ─────────────────────────────
    t0 = time.perf_counter()
    available_levels = list_available_tile_levels(image_id)
    if not available_levels:
        raise ValueError(f"No tiles found for image_id={image_id}")

    tile_level = select_analysis_level(
        available_levels=available_levels,
        requested_level=tile_level,
        default_level=settings.DEFAULT_TILE_LEVEL,
    )
    if tile_level != requested_tile_level:
        _report(
            progress_cb,
            0,
            0,
            f"Requested level {requested_tile_level} unavailable. Using level {tile_level}",
            tile_level,
        )

    tile_refs = list_tiles_at_level(image_id, tile_level)
    timings["list_tiles_s"] = round(time.perf_counter() - t0, 3)

    total = len(tile_refs)
    _report(progress_cb, 0, total, f"Found {total} tiles at level {tile_level}", tile_level)

    if total == 0:
        raise ValueError(
            f"No tiles found for image_id={image_id} at level={tile_level}"
        )

    # Determine grid dimensions from tile coordinates
    max_x = max(t.x for t in tile_refs)
    max_y = max(t.y for t in tile_refs)
    grid_cols = max_x + 1
    grid_rows = max_y + 1
    max_level = max_dzi_level(DZIShape(width=dzi.width, height=dzi.height, tile_size=dzi.tile_size))

    # ── 3. Download, detect tissue, embed, classify ───────────────────
    t0 = time.perf_counter()

    # Initialise heavy models
    embedder = Embedder()
    classifier = Classifier()
    classifier.load()

    timings["model_load_s"] = round(time.perf_counter() - t0, 3)

    # Probability grid for heatmap (-1 = non-tissue)
    prob_grid = np.full((grid_rows, grid_cols), -1.0)

    predictions: List[TilePrediction] = []
    tissue_count = 0
    skipped_count = 0
    flagged_count = 0

    # Process in batches for embedding efficiency
    batch_tiles: List[TileRef] = []
    batch_images = []
    batch_tissue: List[TissueResult] = []

    t_analysis = time.perf_counter()

    for idx, tref in enumerate(tile_refs):
        img = download_tile_image(tref.object_key)
        tissue = detect_tissue(img, threshold=tissue_thresh)

        if not tissue.is_tissue:
            skipped_count += 1
            px, py, w, h = tile_rect_in_fullres(
                shape=DZIShape(width=dzi.width, height=dzi.height, tile_size=dzi.tile_size),
                tile_level=tile_level,
                max_level=max_level,
                tile_x=tref.x,
                tile_y=tref.y,
            )
            predictions.append(
                TilePrediction(
                    tile_x=tref.x,
                    tile_y=tref.y,
                    tile_level=tile_level,
                    pixel_x=px,
                    pixel_y=py,
                    width=w,
                    height=h,
                    is_tissue=False,
                    tissue_ratio=tissue.tissue_ratio,
                    tumor_probability=0.0,
                    label="Background",
                )
            )
            if (idx + 1) % 20 == 0 or idx + 1 == total:
                _report(progress_cb, idx + 1, total, "Analysing tiles", tile_level)
            continue

        tissue_count += 1
        batch_tiles.append(tref)
        batch_images.append(img)
        batch_tissue.append(tissue)

        # Flush batch when full or last tile
        if len(batch_images) >= batch_size or idx + 1 == total:
            if batch_images:
                embeddings = embedder.embed_batch(batch_images, batch_size=len(batch_images))
                cls_results = classifier.predict_batch(embeddings, threshold=threshold)

                for bt, btr, cls_r in zip(batch_tiles, batch_tissue, cls_results):
                    prob_grid[bt.y, bt.x] = cls_r.tumor_probability
                    if cls_r.label == "Tumor":
                        flagged_count += 1
                    px, py, w, h = tile_rect_in_fullres(
                        shape=DZIShape(width=dzi.width, height=dzi.height, tile_size=dzi.tile_size),
                        tile_level=tile_level,
                        max_level=max_level,
                        tile_x=bt.x,
                        tile_y=bt.y,
                    )
                    predictions.append(
                        TilePrediction(
                            tile_x=bt.x,
                            tile_y=bt.y,
                            tile_level=tile_level,
                            pixel_x=px,
                            pixel_y=py,
                            width=w,
                            height=h,
                            is_tissue=True,
                            tissue_ratio=btr.tissue_ratio,
                            tumor_probability=cls_r.tumor_probability,
                            label=cls_r.label,
                        )
                    )

                batch_tiles.clear()
                batch_images.clear()
                batch_tissue.clear()

        if (idx + 1) % 20 == 0 or idx + 1 == total:
            _report(progress_cb, idx + 1, total, "Analysing tiles", tile_level)

    timings["analysis_s"] = round(time.perf_counter() - t_analysis, 3)

    # ── 4. Aggregate ──────────────────────────────────────────────────
    tissue_probs = [p.tumor_probability for p in predictions if p.is_tissue]
    agg_score = float(np.mean(tissue_probs)) if tissue_probs else 0.0
    max_score = float(np.max(tissue_probs)) if tissue_probs else 0.0
    tumor_pct = (flagged_count / tissue_count * 100.0) if tissue_count > 0 else 0.0

    summary = SlideSummary(
        total_tiles=total,
        tissue_tiles=tissue_count,
        skipped_tiles=skipped_count,
        flagged_tiles=flagged_count,
        tumor_area_percentage=round(tumor_pct, 2),
        aggregate_score=round(agg_score, 4),
        max_score=round(max_score, 4),
        aggregation_method="mean",
        threshold=threshold,
    )

    # ── 5. Heatmap ────────────────────────────────────────────────────
    t0 = time.perf_counter()
    _report(progress_cb, total, total, "Generating heatmap", tile_level)
    heatmap_img = generate_heatmap(prob_grid, tile_size=dzi.tile_size)
    heatmap_bytes = heatmap_to_png_bytes(heatmap_img)
    heatmap_key = f"{image_id}/heatmap_level_{tile_level}.png"
    upload_bytes(heatmap_bytes, heatmap_key, content_type="image/png")
    timings["heatmap_s"] = round(time.perf_counter() - t0, 3)

    _report(progress_cb, total, total, "Complete", tile_level)

    return AnalysisResult(
        image_id=image_id,
        tile_level=tile_level,
        dzi={
            "width": dzi.width,
            "height": dzi.height,
            "tile_size": dzi.tile_size,
            "format": dzi.format,
        },
        summary=summary,
        tile_predictions=predictions,
        heatmap_key=heatmap_key,
        timings=timings,
    )


def _report(
    cb: ProgressCallback,
    done: int,
    total: int,
    msg: str,
    tile_level: int | None = None,
) -> None:
    if cb is not None:
        cb(done, total, msg, tile_level)

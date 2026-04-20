"""Pipeline — orchestrates the full region-detection analysis for one slide.

Steps
-----
1. Parse the DZI descriptor to learn the tile grid dimensions.
2. List all tiles at the requested zoom level.
3. Download all tiles concurrently from MinIO.
4. For each tile:
   a. Run tissue detection (skip if background).
   b. Embed tissue tiles with DINOv2 (batched).
   c. Classify each embedding with the sklearn head.
5. Aggregate tile-level results into a slide-level summary.
6. Generate a heatmap overlay image and upload it to MinIO.
7. Return the full result (tile predictions + summary + heatmap path).

Performance notes
-----------------
- ML models (DINOv2 + classifier) are module-level singletons loaded once at
  process startup, not per job.  This avoids a 20-30 s cold-start on every
  analysis request.
- Tile downloads are parallelised with a thread pool (DOWNLOAD_WORKERS threads)
  to saturate network I/O and hide per-tile round-trip latency.
"""

from __future__ import annotations

import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import asdict, dataclass
from typing import Any, Callable, Dict, List, Optional, Tuple

import numpy as np
from PIL import Image

from .classifier import Classifier
from .config import settings
from .embedder import Embedder
from .geometry import DZIShape, max_dzi_level, tile_rect_in_fullres
from .heatmap import TileCell, generate_heatmap, heatmap_to_png_bytes
from .minio_io import (
    TileRef,
    download_tile_image,
    list_available_tile_levels,
    list_tiles_at_level,
    load_tile_manifest,
    parse_dzi,
    upload_json,
    upload_bytes,
)
from .tile_levels import select_analysis_level
from .tissue_detector import TissueResult, detect_tissue


# ── Module-level model singletons ────────────────────────────────────────────
# Loaded once when the module is first used (or explicitly at startup).
# Thread-safe: both models are read-only during inference.

_embedder: Optional[Embedder] = None
_classifiers: Dict[str, Classifier] = {}
_model_lock = threading.Lock()


def get_embedder() -> Embedder:
    global _embedder
    with _model_lock:
        if _embedder is None:
            print("[pipeline] Loading DINOv2 embedder…")
            _embedder = Embedder()
            print("[pipeline] Embedder ready.")
    return _embedder


def get_classifier(model_path: str | None = None) -> Classifier:
    path = model_path or settings.MODEL_PATH
    with _model_lock:
        if path not in _classifiers:
            print(f"[pipeline] Loading sklearn classifier from {path}…")
            clf = Classifier(model_path=path)
            clf.load()
            _classifiers[path] = clf
            print(f"[pipeline] Classifier ready: {path}")
    return _classifiers[path]


def preload_models() -> None:
    """Eagerly initialise both models.  Call this at service startup."""
    get_embedder()
    get_classifier()


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
    flagged_tiles: int
    tumor_area_percentage: float
    aggregate_score: float
    max_score: float
    aggregation_method: str
    threshold: float


@dataclass
class AnalysisResult:
    image_id: str
    job_id: str
    tile_level: int
    dzi: Dict[str, Any]
    summary: SlideSummary
    heatmap_key: str
    summary_key: str
    results_key: str
    timings: Dict[str, float]


# ── Progress callback ─────────────────────────────────────────────────────────

ProgressCallback = Optional[Callable[[int, int, str, int | None], None]]


# ── Concurrent tile downloader ────────────────────────────────────────────────


def _download_tiles_parallel(
    tile_refs: List[TileRef],
    max_workers: int,
    progress_cb: ProgressCallback = None,
    progress_offset: int = 0,
    progress_total: int | None = None,
) -> Dict[str, Optional[Image.Image]]:
    """Download all tiles concurrently. Returns {object_key: PIL.Image | None}."""
    results: Dict[str, Optional[Image.Image]] = {}
    total = progress_total or len(tile_refs)
    done = 0

    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        futures = {pool.submit(download_tile_image, t.object_key): t for t in tile_refs}
        for future in as_completed(futures):
            tref = futures[future]
            try:
                results[tref.object_key] = future.result()
            except Exception as exc:
                print(f"[pipeline] Failed to download {tref.object_key}: {exc}")
                results[tref.object_key] = None
            done += 1
            absolute_done = progress_offset + done
            if absolute_done % 50 == 0 or absolute_done == total:
                _report(
                    progress_cb,
                    absolute_done,
                    total,
                    f"Downloading tiles ({absolute_done}/{total})",
                )

    return results


def _detect_tissue_parallel(
    images: Dict[str, Image.Image],
    threshold: float,
) -> Dict[str, TissueResult]:
    if not images:
        return {}

    results: Dict[str, TissueResult] = {}
    with ThreadPoolExecutor(max_workers=settings.TISSUE_WORKERS) as pool:
        futures = {
            pool.submit(detect_tissue, image, threshold=threshold): object_key
            for object_key, image in images.items()
        }
        for future in as_completed(futures):
            results[futures[future]] = future.result()
    return results


def _iter_chunks(items: List[TileRef], chunk_size: int) -> List[List[TileRef]]:
    return [items[idx: idx + chunk_size] for idx in range(0, len(items), chunk_size)]


# ── Pipeline ──────────────────────────────────────────────────────────────────


def run_analysis(
    job_id: str | None,
    image_id: str,
    tile_level: int | None = None,
    threshold: float | None = None,
    tissue_threshold: float | None = None,
    batch_size: int = 16,
    progress_cb: ProgressCallback = None,
    model_path: str | None = None,
) -> AnalysisResult:
    """Run the full region-detection pipeline for *image_id*."""
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

    # ── 2. Resolve tile level ─────────────────────────────────────────
    t0 = time.perf_counter()
    manifest = load_tile_manifest(image_id)
    available_levels = manifest.available_levels if manifest is not None else list_available_tile_levels(image_id)
    if not available_levels:
        raise ValueError(f"No tiles found for image_id={image_id}")

    tile_level = select_analysis_level(
        available_levels=available_levels,
        requested_level=tile_level,
        default_level=settings.DEFAULT_TILE_LEVEL,
    )
    if tile_level != requested_tile_level:
        _report(
            progress_cb, 0, 0,
            f"Requested level {requested_tile_level} unavailable. Using level {tile_level}",
            tile_level,
        )

    tile_refs = list_tiles_at_level(image_id, tile_level)
    timings["list_tiles_s"] = round(time.perf_counter() - t0, 3)

    total = len(tile_refs)
    _report(progress_cb, 0, total, f"Found {total} tiles at level {tile_level}", tile_level)

    if total == 0:
        raise ValueError(f"No tiles found for image_id={image_id} at level={tile_level}")

    # Grid dims and DZI shape
    max_x = max(t.x for t in tile_refs)
    max_y = max(t.y for t in tile_refs)
    grid_cols = max_x + 1
    grid_rows = max_y + 1
    max_level = max_dzi_level(DZIShape(width=dzi.width, height=dzi.height, tile_size=dzi.tile_size))

    # ── 3. Initialise models (singletons — fast after first call) ──────
    t0 = time.perf_counter()
    embedder = get_embedder()
    classifier = get_classifier(model_path)
    timings["model_load_s"] = round(time.perf_counter() - t0, 3)

    _report(progress_cb, 0, total, "Downloading tiles…", tile_level)

    # ── 4. Chunked tile download + analysis ────────────────────────────
    t_analysis = time.perf_counter()
    download_elapsed = 0.0
    prob_grid = np.full((grid_rows, grid_cols), -1.0)
    predictions: List[TilePrediction] = []
    tissue_count = 0
    skipped_count = 0
    flagged_count = 0
    download_failed_count = 0

    # Track soft-skipped tile refs. These are re-downloaded only if the
    # initial tissue/content pass rejects every tile.
    soft_skipped: List[TileRef] = []

    batch_tiles: List[TileRef] = []
    batch_images: List[Image.Image] = []
    batch_tissue: List[TissueResult] = []

    def flush_batch() -> None:
        nonlocal flagged_count
        if not batch_images:
            return
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
        for image in batch_images:
            image.close()
        batch_tiles.clear()
        batch_images.clear()
        batch_tissue.clear()

    processed_count = 0
    for chunk in _iter_chunks(tile_refs, settings.DOWNLOAD_CHUNK_SIZE):
        download_start = time.perf_counter()
        tile_images = _download_tiles_parallel(
            chunk,
            max_workers=settings.DOWNLOAD_WORKERS,
            progress_cb=progress_cb,
            progress_offset=processed_count,
            progress_total=total,
        )
        download_elapsed += time.perf_counter() - download_start
        tissue_inputs = {
            object_key: image
            for object_key, image in tile_images.items()
            if image is not None
        }
        tissue_results = _detect_tissue_parallel(tissue_inputs, tissue_thresh)

        for tref in chunk:
            img = tile_images.get(tref.object_key)
            processed_count += 1
            if img is None:
                skipped_count += 1
                download_failed_count += 1
                if processed_count % 20 == 0 or processed_count == total:
                    _report(progress_cb, processed_count, total, "Analysing tiles", tile_level)
                continue

            tissue = tissue_results[tref.object_key]

            if not tissue.is_tissue:
                skipped_count += 1
                soft_skipped.append(tref)
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
                img.close()
                if processed_count % 20 == 0 or processed_count == total:
                    _report(progress_cb, processed_count, total, "Analysing tiles", tile_level)
                continue

            tissue_count += 1
            batch_tiles.append(tref)
            batch_images.append(img)
            batch_tissue.append(tissue)

            if len(batch_images) >= batch_size:
                flush_batch()

            if processed_count % 20 == 0 or processed_count == total:
                _report(progress_cb, processed_count, total, "Analysing tiles", tile_level)

    flush_batch()
    timings["download_s"] = round(download_elapsed, 3)
    _report(progress_cb, total, total, "Initial tile pass complete", tile_level)

    # ── Auto-fallback for non-pathology / non-H&E images ─────────────
    # When the H&E saturation check (and variance fallback) reject everything,
    # it means the image is truly uniform-looking at the tile level.  Force
    # all successfully-downloaded tiles through inference so the heatmap is
    # always meaningful.
    if tissue_count == 0 and soft_skipped:
        print(
            f"[pipeline] WARNING: 0 content tiles detected for {image_id} "
            f"(all {len(soft_skipped)} tiles failed tissue/content check). "
            "Falling back to forced inference on all non-blank tiles."
        )
        _report(progress_cb, 0, total, "Retrying with forced content detection…", tile_level)

        # Clear the background predictions written above — we will re-classify
        # these tiles properly and update prob_grid.
        predictions = [p for p in predictions if p.label != "Background"]
        skipped_count = download_failed_count

        fallback_processed = 0
        for chunk in _iter_chunks(soft_skipped, settings.DOWNLOAD_CHUNK_SIZE):
            redownloaded = _download_tiles_parallel(
                chunk,
                max_workers=settings.DOWNLOAD_WORKERS,
            )
            for idx, tref in enumerate(chunk, start=1):
                img = redownloaded.get(tref.object_key)
                fallback_processed += 1
                if img is None:
                    continue

                content = detect_tissue(img, threshold=0.0, variance_fallback=True, std_floor=3.0)
                if not content.is_tissue:
                    img.close()
                    continue

                tissue_count += 1
                skipped_count -= 1
                batch_tiles.append(tref)
                batch_images.append(img)
                batch_tissue.append(content)

                if len(batch_images) >= batch_size:
                    flush_batch()

                if idx % 20 == 0 or idx == len(chunk):
                    _report(
                        progress_cb,
                        fallback_processed,
                        len(soft_skipped),
                        "Forced content analysis",
                        tile_level,
                    )

        flush_batch()

    timings["analysis_s"] = round(time.perf_counter() - t_analysis, 3)

    # ── 6. Aggregate ──────────────────────────────────────────────────
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

    # ── 7. Heatmap ────────────────────────────────────────────────────
    t0 = time.perf_counter()
    _report(progress_cb, total, total, "Generating heatmap", tile_level)

    # Build TileCell list from predictions so the heatmap uses the same
    # full-resolution pixel coordinates as the region-box overlays.
    tile_cells = [
        TileCell(
            pixel_x=p.pixel_x,
            pixel_y=p.pixel_y,
            width=p.width,
            height=p.height,
            tumor_probability=p.tumor_probability if p.is_tissue else -1.0,
        )
        for p in predictions
    ]

    heatmap_img = generate_heatmap(
        prob_grid,
        tile_size=dzi.tile_size,
        image_width=dzi.width,
        image_height=dzi.height,
        tile_cells=tile_cells,
    )
    heatmap_bytes = heatmap_to_png_bytes(heatmap_img)
    artifact_run_id = job_id or f"adhoc-{int(time.time() * 1000)}"
    artifact_prefix = f"{image_id}/analysis/{artifact_run_id}"
    heatmap_key = f"{artifact_prefix}/heatmap_level_{tile_level}.png"
    upload_bytes(heatmap_bytes, heatmap_key, content_type="image/png")
    timings["heatmap_s"] = round(time.perf_counter() - t0, 3)

    results_key = f"{artifact_prefix}/tile_predictions.json"
    summary_key = f"{artifact_prefix}/summary.json"
    upload_json([asdict(prediction) for prediction in predictions], results_key)
    upload_json(
        {
            "image_id": image_id,
            "tile_level": tile_level,
            "dzi": {
                "width": dzi.width,
                "height": dzi.height,
                "tile_size": dzi.tile_size,
                "format": dzi.format,
            },
            "summary": asdict(summary),
            "heatmap_key": heatmap_key,
            "tile_predictions_key": results_key,
            "timings": timings,
        },
        summary_key,
    )

    _report(progress_cb, total, total, "Complete", tile_level)

    return AnalysisResult(
        image_id=image_id,
        job_id=artifact_run_id,
        tile_level=tile_level,
        dzi={
            "width": dzi.width,
            "height": dzi.height,
            "tile_size": dzi.tile_size,
            "format": dzi.format,
        },
        summary=summary,
        heatmap_key=heatmap_key,
        summary_key=summary_key,
        results_key=results_key,
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

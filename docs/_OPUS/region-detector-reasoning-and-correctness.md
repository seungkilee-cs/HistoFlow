# Region Detector: Reasoning, Evidence, and Correctness Pass

> Last Updated: 2026-02-21
>
> Scope: `services/region-detector` implementation and its model/data dependencies.

## 1. Core Question

How did we conclude region-level detection ("red-boxing") was possible, given what data we had, and is the current implementation correct?

Short answer:

1. Region detection is feasible with the current data pipeline.
2. The service architecture is directionally correct.
3. There are correctness gaps that must be fixed before clinical confidence.

## 2. What Data We Actually Had

### 2.1 Training-Time Data (for the classifier head)

The model used by `region-detector` is a sklearn classifier head (`dinov2_classifier.pkl`) trained on PatchCamelyon (PCam):

- Source download script: `services/justin-regression/scripts/download_pcam.py`
- Training script: `services/justin-regression/src/train.py`
- Artifact path: `services/justin-regression/models/dinov2_classifier.pkl`

What this gives us:

- Binary patch labels: tumor vs non-tumor
- DINOv2 embedding -> logistic regression probability mapping

What this does **not** give us:

- Pixel-level tumor boundaries
- Slide-level or pathologist-confirmed region annotations in this repo

### 2.2 Inference-Time Data (for per-slide analysis)

From the existing tiling pipeline we already have:

- DZI descriptor: `<image_id>/image.dzi` (full dimensions, tile size)
- Per-level tiles: `<image_id>/image_files/<level>/<x>_<y>.jpg`
- Stable tile grid coordinates `(level, x, y)` for each patch

From model/runtime configuration we have:

- Embedding backbone: `facebook/dinov2-base`
- Classifier artifact path mounted in compose: `/app/models/dinov2_classifier.pkl`

This combination is enough to score each tile and map scores to regions.

## 3. Why Region Detection Is Possible with This Data

The reasoning chain is:

1. Tiling converts one whole-slide image into deterministic, addressable patches.
2. Every tile already corresponds to a known spatial location in the slide grid.
3. The classifier returns `P(tumor | tile)` for each tile image.
4. Therefore, we can assign a tumor probability to each spatial cell.
5. Aggregating those tile scores yields:
   - a heatmap, and
   - overlay boxes for suspicious regions.

So even without segmentation masks, tile-level probabilities provide practical region cues.

## 4. Implementation Walkthrough (Current Code)

### 4.1 API + job lifecycle

- FastAPI endpoints:
  - `POST /jobs/analyze`
  - `GET /jobs/{job_id}/status`
  - `GET /jobs/{job_id}/results`
  - `GET /health`
- File: `services/region-detector/src/main.py`
- Job storage is in-memory (`_jobs` dictionary), so state is lost on restart.

### 4.2 Data loading from MinIO

- Parse DZI metadata: `parse_dzi(...)`
- List tiles at level: `list_tiles_at_level(...)`
- Download tile image: `download_tile_image(...)`
- Upload generated heatmap: `upload_bytes(...)`
- File: `services/region-detector/src/minio_io.py`

### 4.3 Inference pipeline

- Orchestration entrypoint: `run_analysis(...)`
- Steps:
  1. Parse DZI
  2. List tiles at selected level
  3. Tissue-filter tiles
  4. Embed tissue tiles via DINOv2
  5. Classify embeddings with sklearn head
  6. Aggregate summary metrics
  7. Generate and upload heatmap
- File: `services/region-detector/src/pipeline.py`

### 4.4 Tissue detection

- HSV saturation heuristic:
  - if >=15% pixels exceed saturation floor, tile is tissue.
- File: `services/region-detector/src/tissue_detector.py`

### 4.5 Heatmap generation

- Uses `RdYlGn_r` colormap, transparent non-tissue cells.
- Generates RGBA PNG from a probability grid.
- File: `services/region-detector/src/heatmap.py`

## 5. Correctness Assessment

### 5.1 What is correct

1. Feasibility logic is valid: tile grid + per-tile probabilities -> region-level output.
2. Pipeline composition is coherent and testable in parts.
3. Tissue filtering and heatmap helper functions have unit tests.
4. `tumor_area_percentage` formula is implemented correctly as:
   flagged tissue tiles / total tissue tiles.

### 5.2 What is not fully correct (important)

1. **Overlay geometry is incorrect for non-max DZI levels.**
   - In `pipeline.py`, `pixel_x/pixel_y/width/height` are computed as `tile * tile_size`.
   - This ignores level scaling (`2^(max_level - tile_level)`), so overlay boxes are too small and misplaced when analyzing low/mid levels (for example default level 12).
2. **Local tests do not validate the full inference path.**
   - Current tests cover `tissue_detector` and `heatmap` only.
   - No tests for MinIO I/O, embedding, classifier, or end-to-end coordinate correctness.
3. **Model provenance in operations/docs is easy to confuse.**
   - Runtime expects `dinov2_classifier.pkl` from `justin-regression`.
   - Some older instructions referenced `sk-regression` training flow.
4. **Prototype job store is ephemeral.**
   - In-memory `_jobs` means restart drops all status/results.

### 5.3 Notes (not strictly wrong, but should be explicit)

1. Heatmap is currently generated at one pixel per tile cell (not upscaled by default).
   - This can still be rendered as a coarse overlay if scaled in viewer.
2. PCam-based head may have domain shift for other tissue/site distributions.
   - Feasible for prototype, but requires validation before strong claims.

## 6. Recommended Fix Order

1. Fix tile coordinate scaling in `pipeline.py` (highest impact).
2. Add integration test for coordinate mapping against known DZI dimensions.
3. Add an end-to-end smoke test with a tiny synthetic tile set in MinIO.
4. Persist analysis jobs/results (DB or durable store) instead of in-memory only.
5. Record model metadata in output (model name/version/backbone) for traceability.

## 7. Practical Verdict

Region detection is possible with the data we had because we had:

- deterministic spatial tile coordinates from DZI, and
- a patch classifier that outputs tumor probabilities.

The current implementation is a solid prototype, but it is not fully correct for geometry-sensitive overlays at non-max levels and is not yet robust enough for production-level confidence.

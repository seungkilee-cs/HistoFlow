# ADR-006: Image-to-Classification Pipeline and Initial Regression-First Strategy

Status: Proposed
Date: 2025-12-31

## Context

We are building an end-to-end pipeline that ingests large histopathology images (WSIs or tiled images), processes them, and produces clinically meaningful predictions that can be visualized and consumed by downstream systems. The broader platform already supports:
- Uploading source images to object storage (MinIO/S3-compatible)
- Generating Deep Zoom Image (DZI) tiles for interactive viewing (pyvips → OpenSeaDragon)
- Serving tiles and basic metadata via the Kotlin backend

What is still being finalized is the ML analysis path. We want a pragmatic, incremental path that delivers value early, while remaining modular so future models (patch-classifiers, MIL, segmentation, foundation models) can be plugged in with minimal churn.

## Decision

Start with a regression-first slide-level approach, where an input image (or a downscaled WSI view) is passed through a CNN feature extractor (e.g., ResNet18/50) followed by a lightweight regression head (e.g., scikit-learn Ridge/Logistic/Linear), producing a continuous score in [0,1] interpretable as “tumor likelihood” or “risk score.”

- The service will accept local paths or MinIO URIs.
- Preprocessing will be standardized (resize/crop/normalize) and versioned.
- The output contract will be JSON with the model version, preprocessing params, runtime info, and a regression score that can be directly rendered in UI or thresholded to a binary label.
- The design introduces clear interfaces (adapters) so the “analyzer” can later be swapped for patch-based classification, MIL, or segmentation without breaking callers.

This decision is intentionally incremental: it trades some spatial precision for speed-to-value and code simplicity. The infrastructure and contracts laid down here become the backbone for plugging in richer methods.

## Architecture Overview

```
Upload → MinIO (unprocessed) → (optional) Tiling for viewing (DZI) →
ML Inference Service (FastAPI):
  - IO Adapter: Local path / MinIO URI → local temp
  - Preprocessing: resize/crop/normalize (versioned)
  - Feature Extractor: CNN backbone
  - Head: Regression (or classification)
  - Aggregator (optional): combine patch scores
  - Writer: results JSON to MinIO (ml-results/) and return over HTTP
Backend (Kotlin):
  - Job orchestration & status
  - Serving of overlays and summaries
Frontend (OSD):
  - Image + overlay visualization, metrics, thresholds
```

## Pipeline Steps (High Level)

1) Ingestion
- Source images uploaded to MinIO (uploads bucket), with provenance recorded in backend.

2) Viewing Tiles (already implemented)
- DZI tiles generated and served for interactive zooming.

3) Analysis (initial)
- Input: local path or MinIO URI
- IO Adapter: download from MinIO if URI; validate format/size
- Preprocessing: deterministic transforms (ImageNet normalization) with version tag
- Inference:
  - CNN backbone for feature extraction
  - Regression head outputs a continuous score (0–1)
- Output: JSON record with {score, model version, transforms, runtime}
- Persist: results saved to MinIO under ml-results/<imageId>/analysis_summary.json (and optionally per-image JSONL for batch)

4) Visualization (optional for initial)
- Use the regression score for slide-level indicators; future work adds heatmap overlays.

## Why Start with Regression?

- Speed to value: Minimal code to deliver a clinically interpretable risk score.
- Operational simplicity: No complex patch sampling, no heavy aggregation.
- Expandability: The same backbone + adapter interfaces can later power classification, MIL, or segmentation.
- Calibration friendly: Regression output can be calibrated (e.g., Platt/temperature scaling) and displayed as probabilities.
- Resource efficient: Works on CPU, easy to batch, small model artifacts (sklearn head).

## Alternatives Considered

1) Patch-based Classification (grid sampling)
- Pros: Better spatial resolution; aligns with many public datasets (e.g., PCam)
- Cons: Requires patch extraction, tissue masking, aggregation heuristics; more compute and code.
- When to adopt: After initial regression is stable; enable heatmaps and region-level analytics.

2) Multiple Instance Learning (MIL) for WSIs
- Pros: Learns bag-level labels from tile instances; strong WSI results without pixel labels
- Cons: More complex training/inference; harder to operationalize early.
- When to adopt: Once we have sufficient labeled slides and infra for training pipelines.

3) Semantic Segmentation (U-Net/DeepLab)
- Pros: Pixel-level maps; ideal for overlays and precise quantification
- Cons: Requires pixel-level annotations; expensive to train; heavy inference.
- When to adopt: For specific tasks needing region boundaries and percentages.

4) Foundation Models + Linear Probe
- Pros: Strong zero/low-shot performance; reusable features; small heads
- Cons: Larger backbones; licensing/inference cost; needs careful validation.
- When to adopt: When infra supports large models and we need multi-task flexibility.

5) Classic Feature Engineering + ML (H&E color, texture)
- Pros: Lightweight; interpretable
- Cons: Typically underperforms modern CNNs; brittle across datasets
- When to adopt: As fallbacks or for constrained environments.

## Modularity and Swappability

We will enforce clear interfaces so the analysis method can be swapped with minimal changes:

- IO Adapter
  - Interface: given URI/path → local path; supports MinIO and local FS
  - Replaceable with stream-based readers if needed

- Preprocessor
  - Interface: given PIL/ndarray → torch tensor
  - Versioned transforms; enables A/B of augmentations and stain normalization

- Feature Extractor
  - Interface: given tensor → feature vector (numpy/torch)
  - Backends: torchvision ResNet, foundation embeddings, SSL encoders

- Head (Regressor/Classifier/MIL/Seg)
  - Interface: given features (or image/patch) → prediction dict
  - Swap Ridge for Logistic for classification; plug MIL model without changing IO

- Aggregator
  - Interface: list of patch predictions → slide-level summary
  - Strategies: mean/max/weighted, MIL pooling, uncertainty estimates

- Writer
  - Interface: prediction dict → JSON persisted to MinIO + response body
  - Stable schema with model_version, preprocessing_version, runtime

By keeping callers bound to the API contract (input URIs, output JSON schema), we avoid coupling UI/backend to specific model types.

## Data Contracts (Initial)

Request (inference service):
```json
{
  "image_id": "uuid-123",
  "image_uri": "s3://unprocessed-slides/uploads/uuid-123-slide.png",
  "params": { "resize": 224, "backbone": "resnet18" }
}
```

Response (regression):
```json
{
  "image": "s3://unprocessed-slides/uploads/uuid-123-slide.png",
  "image_id": "uuid-123",
  "model": { "name": "slide_regressor", "feature_backbone": "resnet18", "version": "2025-01-05_ridge_v1" },
  "regression": { "score": 0.83, "raw_score": 1.47 },
  "preprocessing": { "resize": 224, "normalization": "imagenet" },
  "runtime": { "device": "cuda", "inference_ms": 9.7 }
}
```

Future variants add `classification` (label + probabilities), `patch_preds` (JSONL), or `overlay` references.

## Migration Path

- Phase 1: Regression-first on downscaled images (single shot)
- Phase 2: Patch extraction + aggregation → heatmap overlays
- Phase 3: MIL training/inference for WSI-level labels
- Phase 4: Segmentation for pixel-level outputs; introduce overlay pyramids
- Phase 5: Foundation model features with small task-specific heads

## Risks and Mitigations

- Risk: Coarse predictions on downscaled images miss small foci
  - Mitigation: Move to patch-based inference (Phase 2) and MIL (Phase 3)
- Risk: Dataset shift across scanners/stains
  - Mitigation: Stain normalization, calibration, domain adaptation; versioned transforms
- Risk: Operational complexity as methods evolve
  - Mitigation: Maintain strict interfaces and output contracts; comprehensive metadata

## Consequences

- Fast path to an end-to-end predictive system with minimal infra
- A stable contract that enables progressive sophistication
- Early UI integration (thresholded score, summaries), then overlays later

## References

- Ilse et al., "Attention-based Deep Multiple Instance Learning"
- Campanella et al., "Clinical-grade computational pathology using weakly supervised deep learning"
- Macenko/Vahadane stain normalization

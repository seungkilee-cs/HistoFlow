# sk-regression: Modular Image → Classification/Regression Inference

This service fetches images (local or MinIO), preprocesses them, extracts features via a CNN backbone, and runs a lightweight scikit-learn regression head. It emits a continuous score and a thresholded label.

It is designed to be modular: IO adapters, preprocessing, feature backbones, and heads can be swapped without changing the CLI or API contract.

## Features
- Inputs: local paths or MinIO URIs (`s3://bucket/key` or `minio://bucket/key`)
- Preprocessing: torchvision transforms (ImageNet normalization)
- Feature extractor: ResNet18 (torchvision) with global-average-pooled features
- Head: scikit-learn regressor (.pkl via joblib)
- Output: JSON per image (score, label, probabilities, runtime)
- Optional JSONL export for batch runs

## Directory Structure
```
services/sk-regression/
  ├─ src/
  │   ├─ minio_io.py          # MinIO URI parsing/download
  │   ├─ regression_model.py  # ResNet18 features + sklearn regressor
  │   └─ pipeline.py          # Inference orchestration
  ├─ predict.py               # CLI wrapper
  ├─ train_classifier.py      # Example training script (optional)
  ├─ requirements.txt
  ├─ Dockerfile
  └─ docker-compose.yml       # Standalone dev (optional)
```

## Quick Start (choose one)
- Best performance on Apple Silicon (M1/M2): run in a macOS Python venv (can use PyTorch MPS in the future; for now runs CPU unless extended to check for MPS).
- Integrated Docker in master stack (CPU-only, portable): use `docker/docker-compose.base.yml` + `docker/docker-compose.ml.yml` and run predictions with `docker compose exec`.
- Standalone Docker (service-local): use this folder's docker-compose for quick experiments.

Important MinIO endpoint note:
- Inside Docker compose network, use `minio:9000` (service DNS).
- From host/venv, use `localhost:9000`.

## Option A: Integrated with master Docker stack (recommended for portability)
Prerequisites
- MinIO and backend stack from `docker/` directory
- A trained model file at `services/sk-regression/models/slide_regressor.pkl` (or similar)
- Your image already uploaded to MinIO (e.g., `s3://unprocessed-slides/uploads/your-image.png`)

Start the stack (CPU-only ML)
```
# From repo root
# Base + ML compose; bring up CPU profile
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.ml.yml \
  --profile cpu up -d --build
```

Run a prediction (inside container)
```
# Use internal endpoint minio:9000
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.ml.yml \
  exec sk-regression \
  python predict.py \
    --model models/slide_regressor.pkl \
    --images s3://unprocessed-slides/uploads/your-image.png \
    --minio-endpoint minio:9000 \
    --minio-access-key $MINIO_ROOT_USER \
    --minio-secret-key $MINIO_ROOT_PASSWORD \
    --threshold 0.5 \
    --save-jsonl outputs/results.jsonl
```

Volumes
- `services/sk-regression/models` → `/app/models`
- `services/sk-regression/data` → `/app/data`
- `services/sk-regression/logs` → `/app/logs`

## Option B: macOS host venv (Apple Silicon)
This gives best local performance. You can later enhance device selection to use MPS (Metal) if desired.

1) Create/activate venv
```
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
```

2) Install dependencies
```
pip install -r requirements.txt
```

3) Predict on local files
```
python predict.py \
  --model models/slide_regressor.pkl \
  --images data/sample1.jpg data/sample2.jpg \
  --threshold 0.5 \
  --save-jsonl outputs/results.jsonl
```

4) Predict from MinIO
```
python predict.py \
  --model models/slide_regressor.pkl \
  --images s3://unprocessed-slides/uploads/your-image.png \
  --minio-endpoint localhost:9000 \
  --minio-access-key minioadmin \
  --minio-secret-key minioadmin \
  --threshold 0.5
```

## Option C: Standalone Docker (service-local)
Build and start (GPU or CPU profile per this service's compose)
```
# From repo root or this folder
# GPU container (if you have NVIDIA on Linux):
docker compose -f services/sk-regression/docker-compose.yml up -d pathology-classifier

# CPU-only container
docker compose -f services/sk-regression/docker-compose.yml --profile cpu up -d pathology-classifier-cpu
```

Exec prediction (GPU example)
```
docker exec -it pathology-classifier \
  python predict.py \
    --model models/slide_regressor.pkl \
    --images s3://unprocessed-slides/uploads/your-image.png \
    --minio-endpoint localhost:9000 \
    --minio-access-key minioadmin \
    --minio-secret-key minioadmin \
    --threshold 0.5
```

## Running Predictions (CLI contract)
Local files
```
python predict.py \
  --model models/slide_regressor.pkl \
  --images data/sample1.png data/sample2.jpg \
  --threshold 0.5 \
  --save-jsonl outputs/results.jsonl
```

MinIO URIs
```
python predict.py \
  --model models/slide_regressor.pkl \
  --images s3://unprocessed-slides/uploads/uuid-1-slide.png \
           s3://unprocessed-slides/uploads/uuid-2-slide.png \
  --minio-endpoint <endpoint> \
  --minio-access-key <key> \
  --minio-secret-key <secret> \
  --threshold 0.5
```

Example output (per image)
```
{
  "image": "s3://unprocessed-slides/uploads/uuid-123-slide.png",
  "image_id": "uuid-123-slide",
  "model": { "name": "slide_regressor", "feature_backbone": "resnet18", "version": "slide_regressor" },
  "regression": { "score": 0.83, "raw_score": 1.47 },
  "classification": { "label": "Tumor", "probabilities": {"Normal": 0.17, "Tumor": 0.83}, "threshold": 0.5 },
  "preprocessing": { "resize": 224, "normalization": "imagenet" },
  "runtime": { "device": "cpu|cuda", "inference_ms": 9.7 }
}
```

## Full Testing Steps
0) Verify MinIO object and credentials
- Console: http://localhost:9001 (default admin/minioadmin)
- Example URI: `s3://unprocessed-slides/uploads/your-image.png`

1) Prepare a model
- Place `slide_regressor.pkl` under `services/sk-regression/models/`.

2) Test locally (venv)
```
python predict.py --model models/slide_regressor.pkl --images data/sample.jpg
```

3) Test integrated Docker (CPU)
```
# Bring up base + ML
docker compose -f docker/docker-compose.base.yml -f docker/docker-compose.ml.yml --profile cpu up -d --build

# Run prediction via exec (internal endpoint minio:9000)
docker compose -f docker/docker-compose.base.yml -f docker/docker-compose.ml.yml exec sk-regression \
  python predict.py \
    --model models/slide_regressor.pkl \
    --images s3://unprocessed-slides/uploads/your-image.png \
    --minio-endpoint minio:9000 \
    --minio-access-key $MINIO_ROOT_USER \
    --minio-secret-key $MINIO_ROOT_PASSWORD \
    --threshold 0.5
```

4) Test JSONL export
```
python predict.py --model models/slide_regressor.pkl \
  --images data/sample1.jpg data/sample2.jpg \
  --save-jsonl outputs/results.jsonl
cat outputs/results.jsonl
```

5) Device selection
- Current pipeline: uses CUDA if available, otherwise CPU.
- Future enhancement: add MPS (Apple Metal) detection when running on macOS host venv.
- Force CPU: `CUDA_VISIBLE_DEVICES=""`.

## Reset / Cleanup
- Temporary downloads live under system temp (prefix `skreg_`) and are cleaned up best-effort.
- Clear outputs: `rm -rf outputs/`
- Clear venv: `deactivate && rm -rf .venv/`
- Pip cache: `pip cache purge`

## Extending / Swapping Components
- IO: extend `src/minio_io.py` for new schemes or streaming.
- Preprocessing: edit `SlideRegressor._default_transforms()` in `src/regression_model.py`.
- Backbone: swap ResNet18 to other models; keep `.predict_single_image` signature.
- Head: replace sklearn regressor with a classifier; keep output contract.
- Aggregation: add patch extraction + aggregation in `src/pipeline.py` for WSIs.

## Later: Making this a shared ML foundation
- ML base images (CPU and CUDA):
  - Build `ml-base-cpu` (multi-arch linux/arm64+amd64) with shared deps (torch/torchvision CPU, numpy, pillow, opencv-python-headless, joblib, minio, optional FastAPI).
  - Build `ml-base-cuda` (linux/amd64) with CUDA-enabled torch.
  - Make service Dockerfiles accept `ARG BASE_IMAGE` and default to `ml-base-cpu`.
- Compose profiles: `cpu` and `gpu` to switch runtime and resource reservations; `dev` for developer ports/tools.
- Multi-arch: use `docker buildx build --platform linux/amd64,linux/arm64`.
- Optional REST: expose FastAPI `/predict` for orchestration by backend instead of `docker exec` CLI.
- TensorFlow services: either add TF to a specialized base image or create `ml-base-tf-*` to keep other services lean.

## Notes on Apple Silicon (M1/M2)
- Docker on macOS does not expose Metal (MPS) to containers; expect CPU inside Docker.
- For best local performance, use the macOS venv path and (later) enable MPS in the code path.
- In the integrated stack, use CPU profile locally on M1 and GPU profile on Linux GPU nodes in CI/production.

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
  │   ├─ __init__.py
  │   ├─ main.py                # Core prediction logic for the CLI
  │   ├─ train.py               # Core training logic for the CLI
  │   ├─ minio_io.py            # MinIO URI parsing/download
  │   ├─ pipeline.py            # Inference orchestration
  │   ├─ regression_model.py    # Core ResNet18 feature extractor
  │   └─ pathology_classifier.py # Training orchestration and data loading
  ├─ predict.py                 # CLI entry point for prediction
  ├─ train_classifier.py        # CLI entry point for training
  ├─ requirements.txt
  ├─ Dockerfile
  └─ docker-compose.yml         # Standalone dev (optional)
```

## Usage (Integrated Docker Stack)

This service is designed to be run as part of the project's main Docker Compose stack, located in the repository's `/docker` directory.

### Step 1: Start the Services

Make sure Docker Desktop is running. From the repository root, start all services:

```bash
# This brings up the base services (Postgres, MinIO) and the ML services.
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.ml.yml \
  --profile cpu up -d --build
```
*Note: The MinIO console will be available at [http://localhost:9001](http://localhost:9001) (default credentials: `minioadmin`/`minioadmin`).*

### Step 2: Train a New Model

To get meaningful predictions, you must first train the classification head using a dataset. The script is configured to use the [Patch Camelyon (PCam) dataset](https://www.kaggle.com/c/histopathologic-cancer-detection/data).

1.  **Download the Dataset**: Download the PCam HDF5 (`.h5`) files from Kaggle or another source.

2.  **Place the Dataset**: Create the directory `services/sk-regression/data/pcam/` and place the HDF5 files inside, ensuring they have these exact names:
    *   `camelyonpatch_level_2_split_train_x.h5`
    *   `camelyonpatch_level_2_split_train_y.h5`
    *   `camelyonpatch_level_2_split_test_x.h5`
    *   `camelyonpatch_level_2_split_test_y.h5`

3.  **Run Training**: Execute the training script inside the running container. This will process the dataset and save a new `pathology_lr_resnet18.pkl` file to the `services/sk-regression/models/` directory.

    ```bash
    # Note: Training can take a very long time depending on the dataset size.
    docker compose \
      -f docker/docker-compose.base.yml \
      -f docker/docker-compose.ml.yml \
      exec sk-regression python train_classifier.py
    ```
    *__Important Note on Training:__ Training uses PyTorch's `DataLoader`, which requires a significant amount of shared memory (`shm`). The project is pre-configured in `docker/docker-compose.ml.yml` with `shm_size: '1g'` for the `sk-regression` service to prevent crashes during training.*

### Step 3: Run a Prediction

Once you have a trained model, you can run predictions on new images.

1.  **Upload an Image**: Upload an image you want to test (e.g., `my_image.jpg`) to a bucket in MinIO, for example, the `unprocessed-slides` bucket.

2.  **Run Prediction Command**: Use the `exec -T` command to run `predict.py`. The `-T` flag disables the pseudo-tty and prevents terminal crashes.

    ```bash
    # Replace the --images URI with your file's URI.
    docker compose \
      -f docker/docker-compose.base.yml \
      -f docker/docker-compose.ml.yml \
      exec -T sk-regression \
      python predict.py \
        --model models/pathology_lr_resnet18.pkl \
        --images s3://unprocessed-slides/my_image.jpg \
        --minio-endpoint minio:9000 \
        --minio-access-key minioadmin \
        --minio-secret-key minioadmin
    ```

### Volumes
The service is configured with the following volume mounts to sync your local files with the container:
- `services/sk-regression:/app` (Source code)
- `services/sk-regression/data:/app/data` (Training data)
- `services/sk-regression/models:/app/models` (Trained models)
- `services/sk-regression/logs:/app/logs` (Log files)

## Extending / Swapping Components
- IO: extend `src/minio_io.py` for new schemes or streaming.
- Preprocessing: edit `SlideRegressor._default_transforms()` in `src/regression_model.py`.
- Backbone: swap ResNet18 to other models; keep `.predict_single_image` signature.
- Head: replace sklearn regressor with a classifier; keep output contract.
- Aggregation: add patch extraction + aggregation in `src/pipeline.py` for WSIs.

## Notes on Apple Silicon (M1/M2)
- Docker on macOS does not expose Metal (MPS) to containers; expect CPU inside Docker.
- For best local performance, use a local Python venv and enable MPS in the code path (currently not implemented).
- In the integrated stack, use the `cpu` profile locally on M1/M2 machines.

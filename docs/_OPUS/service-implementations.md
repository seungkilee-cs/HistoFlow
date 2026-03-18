# HistoFlow Service Implementation Details

> Last Updated: 2026-02-21

Detailed documentation of each service's internal implementation, API contracts, and configuration.

---

## 1. Backend API Service

### API Endpoints

#### Upload API (`/api/v1/uploads`)

| Method | Path | Request Body | Response | Purpose |
|--------|------|-------------|----------|---------|
| `POST` | `/initiate` | `{fileName, contentType, datasetName?}` | `{uploadUrl, objectName, imageId, datasetName}` | Generate presigned PUT URL for single-file upload |
| `POST` | `/complete` | `{objectName, imageId?, datasetName?}` | `{status, imageId, message}` | Notify backend of upload completion + trigger tiling |

#### Multipart Upload API (`/api/v1/uploads/multipart`)

| Method | Path | Request Body | Response | Purpose |
|--------|------|-------------|----------|---------|
| `POST` | `/initiate` | `{filename, contentType?, partSizeHint?}` | `{uploadId, key, partSize}` | Start S3 multipart upload |
| `POST` | `/presign` | `{uploadId, key, partNumbers[]}` | `{urls: [{partNumber, url}]}` | Get presigned URLs for parts |
| `POST` | `/complete` | `{uploadId, key, parts: [{partNumber, etag}], datasetName?}` | `200 OK` | Complete multipart + trigger tiling |
| `POST` | `/abort` | `{uploadId, key}` | `200 OK` | Abort multipart upload |

#### Tiles API (`/api/v1/tiles`)

| Method | Path | Response | Purpose |
|--------|------|----------|---------|
| `GET` | `/{imageId}/status` | `{imageId, status, message}` | Check tiling job status |
| `GET` | `/{imageId}/image.dzi` | XML (streamed) | DZI descriptor for OpenSeadragon |
| `GET` | `/{imageId}/image_files/{level}/{x}_{y}.jpg` | JPEG (streamed) | Individual tile image |
| `GET` | `/datasets?limit=&continuationToken=&prefix=` | `{datasets[], nextContinuationToken, appliedPrefix}` | List/search available datasets |

### Configuration (`application.yml`)

```yaml
minio:
  endpoint: http://minio:9000          # internal Docker network
  public-endpoint: http://localhost:9000 # browser-accessible for presigned URLs
  access-key: minioadmin
  secret-key: minioadmin
  buckets:
    tiles: histoflow-tiles
    uploads: unprocessed-slides

tiling:
  base-url: http://localhost:8000       # overridden in Docker to http://tiling:8000
  strategy: direct-http                 # extensible: future "rabbitmq" strategy
```

### Key Implementation Notes

- **Presigned URLs use the publicEndpoint** so browsers can reach MinIO directly
- **S3 multipart minimum part size** is 5MB; backend enforces `partSizeHint.coerceAtLeast(5 * 1024 * 1024)`
- **Upload key format:** `uploads/{UUID}-{filename}` — the UUID is extracted as `imageId`
- **TilingTriggerService** serializes payload manually via ObjectMapper for debugging, then sends via RestTemplate
- **Tile caching:** DZI and tile responses include `Cache-Control: public, max-age=31536000` (1 year)

---

## 2. Tiling Service

### API Contract

**`POST /jobs/tile-image`**

Request:
```json
{
  "image_id": "550e8400-e29b-41d4-a716-446655440000",
  "source_bucket": "unprocessed-slides",
  "source_object_name": "uploads/550e8400.../slide.svs",
  "dataset_name": "Patient-123-Biopsy"
}
```

Response (immediate):
```json
{
  "message": "Tiling job accepted and started in the background.",
  "job": { ... }
}
```

### Processing Pipeline

```
1. _download_source_image()
   ├── stat_object() — get size, content_type, etag
   └── fget_object() — download to /tmp/histoflow_tiling/{filename}

2. _generate_tiles()
   ├── pyvips.Image.new_from_file(path, access='sequential')
   └── image.dzsave(base_path, suffix=".jpg[Q=85]", overlap=0, tile_size=256)
       Output: {imageId}/image.dzi + {imageId}/image_files/{level}/{x}_{y}.jpg

3. _upload_tiles()
   ├── Ensure bucket exists
   └── for each file in tiles_dir.rglob("*"):
       └── fput_object(bucket, "{imageId}/{relative_path}", file_path)

4. _write_metadata()
   └── put_object(bucket, "{imageId}/metadata.json", metadata_json)
       Contains: timings, file count, source info, dataset_name

5. Cleanup: remove temp files (in finally block)
```

### Configuration (`config.py`)

```python
class Settings(BaseSettings):
    MINIO_ENDPOINT: str           # e.g. "minio:9000"
    MINIO_ACCESS_KEY: str
    MINIO_SECRET_KEY: str
    MINIO_UPLOAD_BUCKET: str = "histoflow-tiles"
    MINIO_SECURE: bool = False
    TEMP_STORAGE_PATH: str = "/tmp/histoflow_tiling"
```

### Metadata Schema (`{imageId}/metadata.json`)

```json
{
  "image_id": "uuid",
  "dataset_name": "string",
  "source_bucket": "unprocessed-slides",
  "source_object_name": "uploads/uuid/filename.svs",
  "source_size_bytes": 1073741824,
  "source_content_type": "application/octet-stream",
  "tile_upload_bucket": "histoflow-tiles",
  "tile_file_count": 4521,
  "tile_total_size_bytes": 234567890,
  "timings": {
    "download_seconds": 12.345,
    "tiling_seconds": 45.678,
    "upload_seconds": 23.456,
    "total_seconds": 81.479
  },
  "generated_at": "2026-02-21T12:34:56.789+00:00"
}
```

---

## 3. SK-Regression ML Service

### Overview

CLI-based pathology image classifier using DINOv2 embeddings with a custom regression head (`SlideRegressor`).

### Pipeline (`pipeline.py`)

```
1. Load model: SlideRegressor().load_head(model_path)
2. For each image (local path or s3:// URI):
   a. If remote: download_to_temp(uri, minio_config)
   b. predict_single_image(local_path) → (score, raw_score, inference_ms)
   c. classify(score, threshold) → (label, probabilities)
   d. Build result record
3. Optionally save results to JSONL file
4. Cleanup temp directories
```

### Output Schema

```json
{
  "image": "s3://bucket/path.jpg",
  "image_id": "filename_stem",
  "model": { "name": "...", "version": "..." },
  "regression": { "score": 0.87, "raw_score": 0.87 },
  "classification": {
    "label": "Tumor",
    "probabilities": { "Normal": 0.13, "Tumor": 0.87 },
    "threshold": 0.5
  },
  "preprocessing": { "resize": 224, "normalization": "imagenet" },
  "runtime": { "device": "cpu", "inference_ms": 234.5 }
}
```

### Invocation

```bash
docker compose exec sk-regression python -m src.main \
  --model models/head.pkl \
  --images s3://histoflow-tiles/{imageId}/image_files/12/5_3.jpg \
  --minio-endpoint minio:9000 \
  --minio-access-key minioadmin \
  --minio-secret-key minioadmin \
  --threshold 0.5
```

---

## 4. Justin-Regression ML Service

### Overview

Similar to sk-regression but uses a different model loading approach:
- **DINOv2 embedder** (`DinoV2Embedder`) using `facebook/dinov2-base` via Hugging Face `transformers`
- **Classifier head** loaded via `joblib.load()` (sklearn `LogisticRegression` or similar)

### Key Differences from SK-Regression

| Aspect | sk-regression | justin-regression |
|--------|---------------|-------------------|
| Model loader | Custom `SlideRegressor` class | Raw `joblib.load()` |
| Embedder | Built into `SlideRegressor` | Separate `DinoV2Embedder` class |
| Batch support | Yes (via `SlideRegressor`) | Single image only (indexing bug in loop) |
| Output format | Identical schema | Identical schema |
| `minio_io.py` | Duplicated | Duplicated (identical) |

> [!WARNING]
> `justin-regression/src/main.py` has a scoping issue: the image processing loop increments `local` inside the `for` loop but the actual inference code (`img = Image.open(local_path)`) is **outside** the loop body (incorrect indentation at line 30-76). This means only the last image is processed, and `local_path` may not be defined if the image is local rather than remote.

### Invocation

Same CLI pattern as sk-regression:
```bash
docker compose exec sk-regression python -m src.main \
  --model models/classifier.pkl \
  --images s3://histoflow-tiles/{imageId}/... \
  --minio-endpoint minio:9000 \
  --minio-access-key minioadmin \
  --minio-secret-key minioadmin
```

---

## 5. Frontend Application

### Build & Dev

```bash
cd frontend
npm install
npm run dev     # Vite dev server on :5173
npm run build   # Production build (tsc + vite build)
npm run test    # Vitest
npm run lint    # ESLint
```

### Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_BACKEND_URL` | `http://localhost:8080` | Backend API base URL |

### Upload Flow Implementation (`utils/upload.ts`)

1. **Resume support** — Upload state persisted to `localStorage` keyed by `{filename}-{size}-{lastModified}`
2. **Concurrent workers** — 4 parallel upload workers, each processing one part at a time
3. **Batch presigning** — Requests presigned URLs for 8 parts at once to reduce roundtrips
4. **Retry with backoff** — Each part retries 3 times with exponential backoff (300ms × 2^attempt)
5. **Part size** — Default 16MB, minimum 5MB (S3 requirement)
6. **Cleanup** — Persisted state removed after successful completion

### Component Tree

```
App
├── NavBar (inline JSX)
├── Route "/"
│   └── HomePage
│       └── File upload (drag-drop + button)
│           └── uploadFileWithPresignedMultipart()
└── Route "/tile-viewer"
    └── TileViewerPage
        ├── Dataset search with autocomplete
        ├── Dataset suggestion list
        └── ImageViewer
            └── OpenSeadragon instance
```

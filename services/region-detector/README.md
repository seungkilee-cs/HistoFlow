# HistoFlow Region Detector (Prototype)

This service provides tile-level tumour probability analysis ("red-boxing") for histopathology slides. It analyses already-tiled datasets, generates a probability grid, and produces a heatmap overlay that can be displayed on the frontend.

For a focused reasoning + correctness audit based on the current implementation, see:
`docs/_OPUS/region-detector-reasoning-and-correctness.md`

## 1. What Makes This Possible?

The "red-boxing" region detection relies on three core concepts:

1. **DZI Tile Grid**: The slide has already been cut into a grid of 256×256 pixel tiles by the `tiling` service. Instead of analysing the whole 10-gigapixel slide at once, we analyse individual tiles.
2. **DINOv2 Foundation Model**: We use Meta's `dinov2-base` vision transformer to extract a rich 768-dimensional feature embedding for each tile. DINOv2 is a self-supervised model that understands visual features without needing specific medical training data.
3. **PCam Dataset & Logistic Regression**: We train a simple, lightweight Logistic Regression classifier on top of the DINOv2 embeddings using the [PatchCamelyon (PCam)](https://patchcamelyon.grand-challenge.org/) dataset. PCam contains 327,680 colour images (96×96px) of histopathologic scans of lymph node sections, annotated with binary labels indicating the presence of metastatic tissue.
   * By training the classifier head on PCam, the model learns to map DINOv2's generic features to specific "Tumor" vs "Normal" probabilities.

## 2. Design & Detection Logic

The service is a FastAPI application that processes jobs asynchronously. When a request is received for an `image_id`:

1. **Parse DZI & List Tiles**: Retrieves the `image.dzi` descriptor from MinIO to understand the grid dimensions, then lists all available tiles at the requested mathematical zoom level (default: 12).
2. **Tissue Filtering (Skip Background)**: Histopathology slides are mostly glass (white/grey). The `tissue_detector` converts the tile to the HSV colour space and checks the Saturation channel. If less than 15% of the pixels are colourful, the tile is skipped (saving ~70% of compute time).
3. **Batch Embedding**: The remaining tissue tiles are passed through the DINOv2 model in batches (e.g., 16 at a time) to extract the 768-d embedding vectors.
4. **Classification**: The trained sklearn Logistic Regression model (`models/dinov2_classifier.pkl`) predicts the tumor probability `[0.0, 1.0]` for each embedding.
5. **Heatmap Generation**: We build a 2D NumPy array of probabilities (`-1` for non-tissue). A matplotlib colormap (`RdYlGn_r` - Red/Yellow/Green reversed) converts this grid into a semi-transparent PNG overlay.
6. **Persistence & Response**: The heatmap is uploaded to MinIO. The API returns the slide-level summary (percentage of flagged tissue) and the array of `tilePredictions` containing grid coordinates and probabilities.

## 3. Frontend Relay & Percentage Measurement

### How to relay back to the frontend?

The frontend `ImageViewer.tsx` uses OpenSeadragon. OpenSeadragon supports multiple layered tile sources and custom DOM overlays. 

There are two ways the frontend consumes this data:
1. **The Heatmap Image Layer**: The frontend can simply add the MinIO heatmap URL as a new Image Layer with `opacity: 0.5`. This instantly paints the whole tissue with red/green clouds.
2. **Interactive Red Boxes (Overlays)**: The API returns `tilePredictions`. The frontend iterates through this array. For any tile where `tumor_probability > 0.5`, it converts the grid coordinates (`pixel_x`, `pixel_y`) to OpenSeadragon Viewport coordinates and draws a `<div class="red-box">` bounding box. The alpha transparency of the box border can be tied strictly to the probability (e.g., 99% probability = solid red line, 55% probability = faint red line). 

### How is the "Suspicious Region Percentage" calculated?

The slide summary provides the `tumor_area_percentage`. 
This is mathematically calculated as:
`(Number of tiles with tumor_probability > threshold) / (Total number of TISSUE tiles) * 100`

We specifically divide by *tissue* tiles, not *total* tiles, so the percentage reflects the proportion of the patient's actual biopsy that is cancerous, ignoring the blank glass on the slide.

## 4. Full Docker Setup (Detailed)

This section is specifically for bringing up the full stack needed by region-detector:
- backend (`:8080`)
- tiling (`:8000`)
- region-detector (`:8001`)
- MinIO (`:9000`, console `:9001`)

### Option A (Recommended): Service-local setup script

From repo root:
```bash
cd services/region-detector
./scripts/full-docker-setup.sh
```

This wrapper calls the canonical bootstrap script at `scripts/setup-and-start.sh`.

Useful variants:
```bash
# Start frontend too
START_FRONTEND=1 RUN_REGION_TESTS=1 ./scripts/full-docker-setup.sh

# Skip region-detector pytest in startup
START_FRONTEND=0 RUN_REGION_TESTS=0 ./scripts/full-docker-setup.sh
```

### Option B: Manual docker compose startup

From repo root:
```bash
cd docker
docker compose -f docker-compose.base.yml -f docker-compose.dev.yml -f docker-compose.ml.yml --profile cpu --profile dev up -d
```

### Verify service health

From repo root:
```bash
curl -s http://localhost:8080/api/v1/health
curl -s http://localhost:8000/health
curl -s http://localhost:8001/health
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9000/minio/health/ready
```

Expected:
- backend returns JSON with `"status":"UP"`
- tiling returns `{"status":"ok"}`
- region-detector returns `{"status":"ok","service":"region-detector"}`
- MinIO readiness returns HTTP `200`

### Stop the full stack

From repo root:
```bash
cd docker
docker compose -f docker-compose.base.yml -f docker-compose.dev.yml -f docker-compose.ml.yml --profile cpu --profile dev down
```

## 5. Step-by-Step: Run the Full Region Detection Flow

The commands below are from repo root after Docker setup is healthy.

### Step 1: Verify model file exists
```bash
ls -lh services/justin-regression/models/dinov2_classifier.pkl
```

If missing, train it:
```bash
cd services/justin-regression
python src/train.py
```

### Step 2: Start stack (canonical)
```bash
START_FRONTEND=0 RUN_REGION_TESTS=1 scripts/setup-and-start.sh
```

This starts backend, tiling, region-detector, MinIO, and runs region-detector unit tests.

### Step 3: Verify health
```bash
curl -s http://localhost:8080/api/v1/health
curl -s http://localhost:8000/health
curl -s http://localhost:8001/health
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9000/minio/health/ready
```

Expected: backend `UP`, tiling/region-detector `ok`, MinIO `200`.

### Step 4: Upload a slide through backend API
1) Initiate upload and capture fields:
```bash
INIT_JSON=$(curl -sS -X POST http://localhost:8080/api/v1/uploads/initiate \
  -H "Content-Type: application/json" \
  -d '{"fileName":"sample.bmp","contentType":"image/bmp","datasetName":"My Case"}')

echo "$INIT_JSON" | jq .

UPLOAD_URL=$(echo "$INIT_JSON" | jq -r '.uploadUrl')
OBJECT_NAME=$(echo "$INIT_JSON" | jq -r '.objectName')
IMAGE_ID=$(echo "$INIT_JSON" | jq -r '.imageId')
```

2) Upload file bytes to pre-signed URL:
```bash
curl -X PUT "$UPLOAD_URL" \
  -H "Content-Type: image/bmp" \
  --upload-file /path/to/your/sample.bmp
```

3) Mark upload complete (this triggers tiling):
```bash
curl -sS -X POST http://localhost:8080/api/v1/uploads/complete \
  -H "Content-Type: application/json" \
  -d "{\"objectName\":\"$OBJECT_NAME\",\"imageId\":\"$IMAGE_ID\",\"datasetName\":\"My Case\"}" | jq .
```

### Step 5: Wait for tiling completion
```bash
curl -s "http://localhost:8080/api/v1/tiles/$IMAGE_ID/status" | jq .
```

Repeat until `status` becomes `completed`.

### Step 6: Manually trigger analysis (fixed level 12)
```bash
TRIGGER_JSON=$(curl -sS -X POST \
  "http://localhost:8080/api/v1/analysis/trigger/$IMAGE_ID?tileLevel=12")
echo "$TRIGGER_JSON" | jq .
JOB_ID=$(echo "$TRIGGER_JSON" | jq -r '.job_id')
```

### Step 7: Poll analysis status
```bash
curl -s "http://localhost:8080/api/v1/analysis/status/$JOB_ID" | jq .
```

Repeat until `status` is `completed`.

### Step 8: Fetch results and heatmap
```bash
curl -s "http://localhost:8080/api/v1/analysis/results/$JOB_ID" | jq .
curl -s -o heatmap.png "http://localhost:8080/api/v1/analysis/heatmap/$JOB_ID"
file heatmap.png
```

You can also verify in MinIO:
- Console: `http://localhost:9001`
- Bucket: `histoflow-tiles`
- Key: `<IMAGE_ID>/heatmap_level_12.png`

## 6. Service-Level Testing (Region-Detector Only)

### Unit tests
```bash
cd services/region-detector
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
pytest tests -q
```

### Direct API debug (bypass backend)
```bash
curl -X POST http://localhost:8001/jobs/analyze \
  -H "Content-Type: application/json" \
  -d '{"image_id":"<IMAGE_ID>","tile_level":12,"threshold":0.5,"batch_size":16}'

curl http://localhost:8001/jobs/<JOB_ID>/status
curl http://localhost:8001/jobs/<JOB_ID>/results | jq .
```

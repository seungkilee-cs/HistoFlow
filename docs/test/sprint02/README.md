# Sprint 02 - Feature/Tiling Microservice Testing

Testing the Python tiling microservice, also the docker setups.

## 1. Docker Stack Setup (MinIO + Kotlin Backend + Python Tiling)

1. Ensure `.env` files exist:
   - `services/tiling/.env` with `MINIO_ENDPOINT=minio:9000`, credentials, etc.
   - `docker/minio.env` with `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`.
2. Build & start the stack from repo root:
   ```bash
   docker compose \
     -f docker/docker-compose.base.yml \
     -f docker/docker-compose.dev.yml \
     --profile dev up --build
   ```
3. Verify services:
   - MinIO console: <http://localhost:9001>
   - Backend health: `curl http://localhost:8080/api/v1/health`
   - Tiling service: `curl http://localhost:8000/health`

To stop the stack:
```bash
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.dev.yml \
  --profile dev down
```

## 2. Manual Upload & Tiling Test (simulate-upload)

1. Prepare an uploadable slide (e.g., `backend/scripts/CMU-1.tiff`).
2. From repo root, run:
   ```bash
   ./scripts/sprint2/simulate-upload.sh \
     --file backend/scripts/CMU-1.tiff \
     --content-type application/octet-stream
   ```
   Optional flags:
   - `--dataset-name my-dataset`
   - `--backend-url http://localhost:8080`
3. The script outputs `imageId` and `objectName`. Keep these for verification.
4. Confirm upload in MinIO bucket `unprocessed-slides/<imageId>/` via CLI or console.
5. Trigger tiling (if not automated) using the returned values:
   ```bash
   curl -X POST http://localhost:8000/jobs/tile-image \
     -H "Content-Type: application/json" \
     -d '{
           "image_id": "<imageId>",
           "source_bucket": "unprocessed-slides",
           "source_object_name": "<objectName>"
         }'
   ```
6. Verify tiles:
   - DZI descriptor: `curl http://localhost:8080/api/v1/tiles/<imageId>/image.dzi`
   - Sample tile: `curl http://localhost:8080/api/v1/tiles/<imageId>/image_files/0/0_0.jpg --output /tmp/tile.jpg`

## 3. Cleanup

- Stop Compose stack (see above) and remove `/tmp/histoflow_tiling` if created. Should be autoremoved
- Optionally delete MinIO buckets using the console or `mc`.
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

## 2. Automated Upload & Tiling Flow (Sprint 03)

With the synchronous trigger + polling workflow in place, prefer the Sprint 03 scripts:

1. Prepare a slide file (e.g., `backend/scripts/CMU-1.tiff`).
2. Run the upload simulation (handles initiate → upload → complete → status):
   ```bash
   ./scripts/sprint03/simulate-upload.sh \
     --file backend/scripts/CMU-1.tiff \
     --content-type application/octet-stream
   ```
   Optional flags:
   - `--dataset-name my-dataset`
   - `--backend-url http://localhost:8080`
   - `--poll-interval 5` (seconds between status checks)
   - `--max-attempts 60`
3. The script prints tiling status until completion. On success it exits `0` and shows the final message.
4. For a full end-to-end verification (including fetching artifacts), run:
   ```bash
   ./scripts/sprint03/e2e-upload-and-tile.sh \
     --file backend/scripts/CMU-1.tiff \
     --dataset-name my-dataset
   ```
   This script wraps the simulation, then downloads the generated `image.dzi` and a sample tile to `tmp/e2e-artifacts/`.

## 3. Manual Verification (Optional)

If you need to inspect MinIO or call the APIs yourself:

1. Confirm the raw upload: bucket `unprocessed-slides/<imageId>/`.
2. Check tiling output: bucket `histoflow-tiles/<imageId>/`.
3. Download artifacts manually:
   ```bash
   curl http://localhost:8080/api/v1/tiles/<imageId>/image.dzi
   curl http://localhost:8080/api/v1/tiles/<imageId>/image_files/0/0_0.jpg --output /tmp/tile.jpg
   ```

## 4. Cleanup

- Stop Compose stack (see above) and remove `/tmp/histoflow_tiling` or `tmp/e2e-artifacts` if created.
- Optionally delete MinIO buckets using the console or `mc`.
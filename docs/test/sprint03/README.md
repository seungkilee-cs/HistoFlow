# Sprint 03 – End-to-End Upload & Tiling Test

This guide covers the full workflow for Sprint 3: spinning up the Docker stack, simulating the
frontend upload, triggering the tiling microservice automatically, and verifying the results.

## 1. Start the Docker Environment

From the repository root, build and start the development stack (MinIO + Kotlin backend + Python tiling):

```bash
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.dev.yml \
  --profile dev up --build
```

Keep this running in a terminal. Optional health checks:

```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8000/health
# MinIO console is available at http://localhost:9001
```

## 2. Upload Simulation (CLI)

### Option A – Initiate → Upload → Complete → Poll

Run the Sprint 03 simulation script (no frontend required):

```bash
./scripts/sprint03/simulate-upload.sh \
  --file backend/scripts/CMU-1.tiff \
  --content-type application/octet-stream
```

Common options:

- `--dataset-name my-dataset`
- `--backend-url http://localhost:8080`
- `--poll-interval 5` (seconds between status checks)
- `--max-attempts 60`

The script:

1. Calls `/api/v1/uploads/initiate` to fetch a presigned URL.
2. Uploads the file to MinIO.
3. Calls `/api/v1/uploads/complete` to trigger tiling.
4. Polls `/api/v1/tiles/<imageId>/status` until the job completes (exit code 0 on success).

### Option B – Full E2E Flow with Artifacts

```bash
./scripts/sprint03/e2e-upload-and-tile.sh \
  --file backend/scripts/CMU-1.tiff \
  --dataset-name my-dataset
```

This wraps the simulation, then downloads the resulting `image.dzi` and a sample tile to
`tmp/e2e-artifacts/`. Customize via:

- `--poll-interval`, `--max-attempts`
- `--output-dir`
- `--level`, `--coord`
- `--skip-artifacts`

## 3. Manual Verification (Optional)

Inspect MinIO or query the APIs manually:

```bash
# Raw upload should appear in `unprocessed-slides/<imageId>/`
curl http://localhost:8080/api/v1/tiles/<imageId>/image.dzi
curl http://localhost:8080/api/v1/tiles/<imageId>/image_files/0/0_0.jpg --output /tmp/tile.jpg
```

## 4. Cleanup

When finished:

```bash
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.dev.yml \
  --profile dev down
```

Remove any temporary artifacts (`tmp/e2e-artifacts/`, `/tmp/histoflow_tiling`) as needed or wipe the
MinIO buckets via console/CLI.

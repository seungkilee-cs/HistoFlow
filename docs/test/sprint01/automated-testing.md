# Sprint 01 Automated Tile Serving Checks [WIP]

> Automated e2e testing script WIP

## Prerequisites

Ensure the following tools are installed on macOS:
- **Homebrew** (`/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"`)
- **Python 3.12+** (`brew install python@3.12`)
- **Node.js 18+** (`brew install node`)
- **Java 17+** (`brew install openjdk@17` and export `JAVA_HOME`)
- **MinIO server & client** (`brew install minio/stable/minio minio/stable/mc`)
- **pyvips binding prerequisites** (`brew install vips`)

Grant Terminal automation permission (System Settings → Privacy & Security → Automation) so scripts can open new tabs.

Make helper scripts executable:
```bash
chmod +x scripts/api-smoke-test.sh
chmod +x backend/scripts/full-regenerate.sh
chmod +x backend/scripts/cleanup-tiles.sh
chmod +x scripts/dev-start.sh
```

## 1. Launch Core Services

Run the consolidated startup script (spawns new tabs in the current Terminal window):
```bash
./scripts/dev-start.sh
```
What it does:
- Starts MinIO (`minio server ~/minio-data --console-address ":9001"`)
- Runs the backend (`./gradlew bootRun`)
- Runs the frontend (`npm run dev`)
- Opens `http://localhost:3000`

## 2. Full Tile Regeneration Pipeline

Automate the Python workflow, including MinIO cleanup and environment bootstrap:
```bash
cd backend/scripts
./full-regenerate.sh [<image_path> <image_id>]
```
Defaults:
- `image_path`: `JPG_Test.jpg`
- `image_id`: `test-image-001`

What happens:
1. Removes existing `venv/`
2. Executes `setup.sh` to rebuild the Python environment
3. Sources `dev.sh` to activate the venv
4. Clears MinIO objects under `histoflow-tiles/<image_id>/`
5. Runs `generate_test_tiles.py` to rebuild and upload tiles

## 3. API Smoke Test

Verify the backend endpoints quickly:
```bash
./scripts/api-smoke-test.sh
```
- Required checks: DZI descriptor and a sample tile.
- Optional: `/actuator/health` (ignored if missing).
- Exit code `0` means required endpoints returned 2xx.

Override target image or API host if necessary:
```bash
IMAGE_ID=my-slide API_BASE_URL=http://localhost:8080 ./scripts/api-smoke-test.sh
```

## 4. Frontend Smoke Check

Confirm the renamed viewer route is live (manually open in your browser):
```bash
# copy into browser
http://localhost:3000/tile-viewer
```
- Ensure the page renders.
- The control panel should show the default `test-image-001` dataset.

## 5. Resetting Between Runs

To reset everything quickly:
```bash
# Stop all spawned processes (close terminals or Ctrl+C)
# Optionally wipe MinIO data
rm -rf ~/minio-data/.minio.sys/buckets/histoflow-tiles

# Rerun steps 1-3
```

This sequence delivers a repeatable automated QA flow for Sprint 01 tile serving. Update the scripts if service ports or credentials change.

## Bonus: One-Command Master Script

Execute the full Sprint 1 flow (services → tile regeneration → API validation → reminder to open viewer):
```bash
chmod +x scripts/sprint1-master.sh
./scripts/sprint1-master.sh [<image_path> <image_id>]
```
- Invokes `dev-start.sh --auto`, `backend/scripts/full-regenerate.sh`, `api-smoke-test.sh`, and prints the Tile Viewer URL for manual opening.
- Defaults to `backend/scripts/JPG_Test.jpg` and `test-image-001` when no arguments are supplied.
- Requires macOS Terminal access for launching service windows (uses `osascript`).

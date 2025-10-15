# Sprint 01 Manual Tile Serving Verification

Last updated: 2025-10-14

Follow the steps below immediately after pulling the latest code to validate tile serving end-to-end.

## Prerequisites

- macOS with Homebrew installed
- Node.js 18+
- Java 17+
- Python 3.11.x (will be managed by scripts)
- MinIO CLI (`brew install minio/stable/mc`) optional but helpful

## Step-by-Step Checklist

1. **Install backend dependencies**
   ```bash
   cd backend
   ./gradlew clean build
   ```

2. **Start MinIO** (new terminal)
   ```bash
   minio server ~/minio-data --console-address ":9001"
   ```
   - Access console at `http://localhost:9001`
   - Default credentials: `minioadmin / minioadmin`

3. **Run backend API** (new terminal)
   ```bash
   cd backend
   ./gradlew bootRun
   ```
   - API base URL: `http://localhost:8080`

4. **Install frontend dependencies**
   ```bash
   cd frontend
   npm install
   ```

5. **Start frontend dev server** (new terminal)
   ```bash
   cd frontend
   npm run dev
   ```
   - Open `http://localhost:3000`

6. **Prepare Python environment for tile generation**
   ```bash
   cd backend/scripts
   ./setup.sh        # creates venv + installs pyvips/minio
   source ./dev.sh   # activates venv and prints quick usage
   ```

7. **Generate Deep Zoom tiles**
   ```bash
   python generate_test_tiles.py JPG_Test.jpg test-image-001
   ```
   - Confirms upload to MinIO bucket `histoflow-tiles`

8. **Manual verification**
   - Visit `http://localhost:8080/api/v1/tiles/test-image-001/image.dzi`
   - Sample tile: `http://localhost:8080/api/v1/tiles/test-image-001/image_files/0/0_0.jpg`
   - Open browser at `http://localhost:3000/tile-viewer`
   - Load `test-image-001` and confirm zoom/pan works without 404s

## Cleanup (Optional)

- Stop MinIO (`Ctrl+C` in terminal)
- Stop backend (`Ctrl+C`)
- Stop frontend (`Ctrl+C`)
- Deactivate Python venv (`deactivate`)

# Sprint 1 - Feature/Tile Serve Testing

> Verbose Manual Verification without any scripts is in [manual-testing.md](manual-testing.md), but I recommend just following this README.

## Smoke Testing
![Test Image](../../../assets/251014_22_49_20_HistoFlow.png)

## Prerequisites

- macOS with Homebrew installed
- Node.js 18+
- Java 17+ 
- Python 3.12 (will be managed by scripts)
- MinIO CLI

## Manual Testing

### 0. Optional: Script Permissions
To make your life easier, you can run this from the root directory and proceed. You don't have to do this but it makes thinigs easier.

```bash
chmod +x scripts/*.sh
```

### 1. Start MinIO

```bash
minio server ~/minio-data --console-address ":9001"
```

or a wrapper script

```bash
./scripts/minio-start.sh
```

### 2. Build and Start Backend

```bash
cd backend/ && ./gradlew clean build && ./gradlew bootRun
```

or a wrapper script

```bash
./scripts/backend-start.sh
```

## 3. Manual Tile Generation
This tiling would be handled by either at the upload time or at the request time, but for the tile serving testing, we will generate tiles manually using a python script to generate DZI and upload it to MinIO.

### 3.1 Python Set Up

I automated the setup process with a script so you don't have to mess with venvs and dependencies.

```bash
cd backend/scripts
./setup.sh
```

### 3.2. Python venv activation
While you are in the `backend/scripts` run the following command to activate the venv. 
(I want to wrap that in a script to automate the whole workflow, but with pyenv shell bahvior it is tricky)

```bash
source ./_dev.sh
```

### 3.3. Place the image file in the `backend/scripts` directory
You can use any image file you have.

For small file I used [JPG_Test.jpg](https://commons.wikimedia.org/wiki/File:JPG_Test.jpg) which is about 20MB.

For large file I used [CMU-1.tiff](https://openslide.cs.cmu.edu/download/openslide-testdata/Generic-TIFF/) which is about 200MB. You can also get this directly by running 

```bash
cd backend/scripts
wget https://openslide.cs.cmu.edu/download/openslide-testdata/Generic-TIFF/CMU-1.tiff
```

Takes a while to download.

### 3.4. Generate Deep Zoom Tiles
Now, we will call the python script to generate the file

```bash
python generate_test_tiles.py [YOUR-IMAGE-FILE] [OUTPUT-IMAGE-NAME]
```

You will use [OUTPUT-IMAGE-NAME] in the frontend to search for the image. Even when you manually test the API, you can:

```bash
curl "http://localhost:8080/api/v1/tiles/datasets?limit=10&prefix=[OUTPUT-IMAGE-NAME]"
```

and you should see the information in response.

### 3.5. API Endpoint Check

You can check the API endpoint by running the following command from the root directory:

```bash
./scripts/api-smoke-test.sh
```

## 4. Frontend Tile Viewer Check
### 4.1. Install and Start Frontend

```bash
cd frontend/
npm install
npm run dev
```

### 4.2. Open Browser 
Open `http://localhost:3000` and verify you can:
- Load the tile viewer tab
- Search / Click to find the dataset DZI
- See the image tiles are served and loaded
- Zoom in and out
- Pan around
- Do all this without screen getting too slow 

## 5. Clean Up

You can either manually remove the tiles from MinIO from the console `localhost:9001` or run the following command from the root directory:

```bash
rm -rf [YOUR-MINIO-DATA-DIRECTORY]/buckets/histoflow-tiles
```

Or, you can remove specific image tiles by running the following command from the `backend/scripts` directory:

```bash
./cleanup-tiles.sh [OUTPUT-IMAGE-NAME]
```

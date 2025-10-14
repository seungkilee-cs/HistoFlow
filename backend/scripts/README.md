# Tile Generation Scripts

Python 환경 및 타일 생성 스크립트 (Backend 전용)

**Scope**: 이 디렉토리는 타일 생성용 Python 환경만 관리합니다.  
**전체 개발 환경 시작**: `/scripts/dev-start.sh` 사용

**한국어 가이드**: [README.ko.md](README.ko.md) | [QUICKSTART.ko.md](QUICKSTART.ko.md)

## Setup

### Quick Setup (Recommended)

```bash
cd backend/scripts
./setup.sh
```

This will:
1. Install vips (via Homebrew)
2. Detect an SSL-enabled Python (prefers system python3, falls back to Homebrew `python@3.12`)
3. Create virtual environment
4. Install Python packages (pyvips, minio)

### Manual Setup

macOS:
```bash
brew install vips
brew install pyenv

# Install Python 3.11.9
pyenv install 3.11.9
pyenv local 3.11.9

# Create virtual environment
python -m venv venv
source venv/bin/activate

# Install packages
pip install -r requirements.txt
```

Ubuntu/Debian:
```bash
sudo apt-get install libvips-tools
pip3 install pyvips minio
```

### 2. Get Test Image

Convention: Place test images in `backend/scripts/` directory

Option A: Download CMU test slide (recommended):
```bash
cd backend/scripts
wget https://openslide.cs.cmu.edu/download/openslide-testdata/Generic-TIFF/CMU-1.tiff
```

Option B: Use your own image:
- Any `.tiff`, `.jpg`, `.png`, or other format supported by pyvips
- Copy to `backend/scripts/` directory:
  ```bash
  cp /path/to/your/image.tiff backend/scripts/
  ```

## Usage

### Option 1: Using dev.sh (Recommended)

```bash
cd backend/scripts
./dev.sh
# This activates venv and opens a new shell
# Then run:
python generate_test_tiles.py <image_path> <image_id>
```

### Option 2: Manual

```bash
cd backend/scripts
source venv/bin/activate
python generate_test_tiles.py <image_path> <image_id>
deactivate
```

Example:
```bash
python3 generate_test_tiles.py CMU-1.tiff test-image-001
```

### Option 3: Full pipeline automation

```bash
cd backend/scripts
./full-regenerate.sh [<image_path> <image_id>]
```

Default 값:
- `image_path`: `JPG_Test.jpg`
- `image_id`: `test-image-001`

하는 일:
1. 기존 `venv/` 삭제
2. `setup.sh` 실행 (venv 재생성 + 의존성 설치)
3. `dev.sh` 통해 가상환경 활성화
4. MinIO 버킷 `histoflow-tiles/<image_id>/` 비우기 (가능하면)
5. `generate_test_tiles.py` 실행 (타일 생성 & 업로드)

### What It Does

1. Loads the image using pyvips
2. Generates DZI pyramid:
   - Creates multiple zoom levels
   - Slices each level into 256×256 JPEG tiles
   - Generates `image.dzi` descriptor XML
3. Uploads to MinIO:
   - Bucket: `histoflow-tiles`
   - Structure: `{image_id}/image.dzi` and `{image_id}/image_files/{level}/{x}_{y}.jpg`
4. Cleans up local temporary files

### Verify Upload

1. MinIO Console: http://localhost:9001
   - Login: `minioadmin` / `minioadmin`
   - Check bucket `histoflow-tiles`
   - Verify folder structure

2. Backend API:
   ```bash
   # DZI descriptor
   curl http://localhost:8080/api/v1/tiles/test-image-001/image.dzi
   
   # Sample tile
   curl http://localhost:8080/api/v1/tiles/test-image-001/0/0_0.jpg --output test.jpg
   open test.jpg
   ```

3. Frontend: http://localhost:3000/test-viewer
   - Image should load automatically
   - Pan and zoom should work smoothly

## Troubleshooting

### `ModuleNotFoundError: No module named 'pyvips'`
```bash
pip3 install pyvips minio
```

### `vips: unable to call ...`
```bash
brew install vips  # macOS
sudo apt-get install libvips-tools  # Ubuntu
```

### `S3Error: Bucket does not exist`
- Ensure MinIO is running: `minio server ~/minio-data --console-address ":9001"`
- Script will auto-create bucket if missing

### Tiles don't appear in frontend
- Check backend is running: `./gradlew bootRun`
- Check browser console for errors (F12)
- Verify imageId matches: default is `test-image-001`

## File Structure After Generation

```
backend/scripts/
├── setup.sh           # Python 환경 설정
├── dev.sh             # Python 가상환경 활성화
├── generate_test_tiles.py
├── full-regenerate.sh # 전체 파이프라인 자동화
└── MinIO bucket: histoflow-tiles/
    └── test-image-001/
        ├── image.dzi                    # DZI descriptor XML
        └── image_files/
            ├── 0/                       # Zoom level 0 (thumbnail)
            │   └── 0_0.jpg
            ├── 1/                       # Zoom level 1
            │   ├── 0_0.jpg
            │   └── 1_0.jpg
            ├── 2/                       # Zoom level 2
            │   ├── 0_0.jpg
            │   ├── 1_0.jpg
            │   ├── 2_0.jpg
            │   └── 3_0.jpg
            └── ...                      # More zoom levels
        └── ...                      # More zoom levels
```

## Notes

- Tile format: JPEG with 85% quality
- Tile size: 256×256 pixels
- Overlap: 0 pixels (no overlap between tiles)
- Pyramid strategy: One tile at lowest zoom level
- Cleanup: Local tiles deleted after upload (saves disk space)

## For Production

In later sprint , we'll replace this manual script with:
- Automated tile generation on image upload
- Either Kotlin backend integration or Python microservice
- Background job processing for large images
- Progress tracking and error handling

For now, this script is sufficient for Sprint 1 testing.

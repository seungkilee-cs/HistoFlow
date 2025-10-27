# Quickstart 가이드 한글 For Justin

## 왜 백엔드에 파이썬을? 

이미지를 OpenSeadragon으로 보려면 타일로 쪼개야함
근데 수동으로 하기 귀찮아서 만든 스크립트

이미지 하나 넣으면 진행되는 과정이 대충:
1. DZI 피라미드 구조로 타일 생성 (pyvips 사용)
2. MinIO에 자동 업로드
3. 로컬 임시파일 정리

나중에 백엔드에 통합할 예정이지만 일단 Sprint 1 테스트용으로 이거 씀

---

## 처음 설정 (한번만 하면 됨)

```bash
cd backend/scripts
./setup.sh
```

이거 실행하면:
- vips 설치 (이미지 처리 라이브러리)
- pyenv 설치 (파이썬 버전 관리)
- Python 3.12 설치
- 가상환경 만들고 패키지 설치

끝나면 터미널 재시작하거나 `source ~/.zshrc` 실행

---

## 스크립트로 자동 타일 생성 할 때

```bash
cd backend/scripts

# 1. 가상환경 활성화
source venv/bin/activate

# 2. 스크립트 실행
python generate_test_tiles.py <이미지파일> <이미지ID>

# 3. 끝나면 비활성화 (선택)
deactivate
```

---

## 예시

```bash
cd backend/scripts
source venv/bin/activate
python generate_test_tiles.py JPG_Test.jpg test-image-001
```

실행하면:
```
Starting tile generation for: test-image-001

Loading image: JPG_Test.jpg
  Image size: 2048 x 1536
  Bands: 3, Format: uchar

Generating DZI tiles...
  Output: ./tiles_output/test-image-001

Tiles generated successfully
  Total files: 127

Connecting to MinIO...
  Bucket exists: histoflow-tiles

Uploading to MinIO bucket: histoflow-tiles
  Uploaded 100 files...
Upload complete: 127 files uploaded to MinIO
  View in MinIO Console: http://localhost:9001

Cleaning up local tiles...

Done! Test your tile serving:
  DZI: http://localhost:8080/api/v1/tiles/test-image-001/image.dzi
  Tile: http://localhost:8080/api/v1/tiles/test-image-001/0/0_0.jpg

  Open frontend: http://localhost:3000/test-viewer
```

---

## 주의할 점

### "vips: command not found"
pyvips는 그냥 파이썬API라서 결국 vips를 따로 시스템에 깔아둬야함
```bash
brew install vips
```

### "pyenv: command not found"
pyenv는 파이썬 버전 관리 할 필요 없이 nvm처럼 쓰는거라 깔아줘야함. 아니면 글로벌로 깔아야 하는데 그게 너무 지저분해서
```bash
brew install pyenv

# ~/.zshrc에 추가:
export PYENV_ROOT="$HOME/.pyenv"
export PATH="$PYENV_ROOT/bin:$PATH"
eval "$(pyenv init -)"

# 적용
source ~/.zshrc
```

### "ModuleNotFoundError: No module named 'pyvips'"
가상환경 안켰거나 패키지 안깔림
```bash
source venv/bin/activate
pip install -r requirements.txt
```

### 가상환경이 이상함
그냥 다시 만들어라
```bash
rm -rf venv
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### MinIO 연결 안된다고 나오면
터미널 바꿔서 다시 실행
```bash
# 다른 터미널에서
minio server ~/minio-data --console-address ":9001"
```

### 백엔드 연결 안됨
백엔드 서버는 월래 gradle로 빌드해서 실행하는거라 100%가 나오는게 아니라 85% 즈음에서 멈추고 execute 뜸. 그러면 실행 된거임.
```bash
# 다른 터미널에서
cd backend
./gradlew bootRun
```

---

## 파일 설명

- `generate_test_tiles.py` - 메인 스크립트
- `requirements.txt` - Python 패키지 목록
- `.python-version` - pyenv용 Python 버전 (3.11.9)
- `setup.sh` - 환경 자동 설정 스크립트
- `dev.sh` - 개발용 빠른 실행 스크립트
- `venv/` - 가상환경 (setup.sh가 만듦)
- `.gitignore` - venv랑 테스트 이미지 git 제외

---

## 설치되는 것들

- **vips** (시스템): 이미지 처리 C 라이브러리
  - pyvips는 이거 없으면 안돌아감
  - 의료 이미지처럼 큰 파일 처리할때 필수
  
- **pyenv** (시스템): Python 버전 관리
  - 프로젝트마다 다른 Python 버전 쓸 수 있게 해줌
  - 시스템 Python 건드리지 않음
  
- **Python 3.11.9** (pyenv로 설치): 이 프로젝트용 Python
  - 최신 안정 버전
  - 3.11이 3.10보다 빠름
  
- **pyvips** (Python 패키지): vips의 Python 바인딩
  - DZI 타일 생성용
  - PIL/Pillow보다 훨씬 빠름
  
- **minio** (Python 패키지): MinIO/S3 클라이언트
  - 생성된 타일 업로드용
  - AWS S3 SDK 호환

---

## 나중에 할 일

지금은 수동으로 스크립트 돌려서 타일 만들지만
나중에는:
- 백엔드에서 이미지 업로드하면 자동으로 타일 생성
- 진행상황 표시
- 에러 처리 제대로
- 여러 이미지 동시 처리

일단 Sprint 1 테스트용으로는 이거면 충분함

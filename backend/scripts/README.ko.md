# 타일 생성 스크립트

Python 환경 및 타일 생성 스크립트 (Backend 전용)

범위: 이 디렉토리는 타일 생성용 Python 환경만 관리  
전체 개발 환경: `/scripts/dev-start.sh` 사용

> 그냥 `backend/scripts/setup.sh`를 쓰면 쉽게 설치 가능함

## 이게 뭐임?

의료 이미지를 웹에서 보려면 타일로 쪼개야함 (OpenSeadragon 쓰려고)

이 스크립트가 하는 일은 대충:
1. 이미지를 받아서
2. 여러 줌 레벨로 256x256 타일 생성 (DZI 피라미드)
3. MinIO에 업로드
4. 임시파일 정리 (로컬 스토리지 쓰다보니)

---

## 설정

### 빠른 설정 (추천)

```bash
cd backend/scripts
./setup.sh
```

이거 하나면 끝
- vips 설치
- pyenv 설치
- Python 3.11.9 설치
- 가상환경 만들기
- 패키지 설치 (pyvips, minio)

### 수동 설정 (귀찮긴 한데 혹시 에러나고 하면 참고용)

macOS:
```bash
brew install vips
brew install pyenv

# Python 3.11.9 설치
pyenv install 3.11.9
pyenv local 3.11.9

# 가상환경 만들기
python -m venv venv
source venv/bin/activate

# 패키지 설치
pip install -r requirements.txt
```

Ubuntu/Debian:
```bash
sudo apt-get install libvips-tools
pip3 install pyvips minio
```

---

## 테스트 이미지 준비

규칙: 테스트 이미지는 `backend/scripts/` 디렉토리에 넣기

### 옵션 1: CMU 테스트 슬라이드 다운로드 (추천)
```bash
cd backend/scripts
wget https://openslide.cs.cmu.edu/download/openslide-testdata/Generic-TIFF/CMU-1.tiff
```

### 옵션 2: 아무거나 큰 이미지 쓰기
- `.tiff`, `.jpg`, `.png` 등 pyvips가 읽을 수 있는 포맷이면 됨
- `backend/scripts/` 디렉토리에 복사:
  ```bas큰
  cp /path/to/your/image.tiff backend/scripts/
  ```

---

## 사용법

### 방법 1: dev.sh 사용 (추천)

```bash
cd backend/scripts
./dev.sh
# 가상환경 켜지고 새 쉘 열림
# 그 다음:
python generate_test_tiles.py <이미지파일> <이미지ID>
```

### 방법 2: 수동

```bash
cd backend/scripts
source venv/bin/activate
python generate_test_tiles.py <이미지파일> <이미지ID>
deactivate
```

예시:
```bash
python generate_test_tiles.py CMU-1.tiff test-image-001
```

### 뭐가 일어남?

1. 이미지 로드: pyvips로 이미지 읽기
2. DZI 피라미드 생성:
   - 여러 줌 레벨 만들기
   - 각 레벨을 256×256 JPEG 타일로 쪼개기
   - `image.dzi` XML 파일 생성
3. MinIO 업로드:
   - 버킷: `histoflow-tiles`
   - 구조: `{이미지ID}/image.dzi` + `{이미지ID}/image_files/{레벨}/{x}_{y}.jpg`
4. 로컬 파일 정리: 임시 타일 삭제

### 업로드 확인

1. MinIO 콘솔: http://localhost:9001
   - 로그인: `minioadmin` / `minioadmin`
   - 버킷 `histoflow-tiles` 확인
   - 폴더 구조 확인

2. 백엔드 API:
   ```bash
   # DZI 파일
   curl http://localhost:8080/api/v1/tiles/test-image-001/image.dzi
   
   # 타일 샘플
   curl http://localhost:8080/api/v1/tiles/test-image-001/0/0_0.jpg --output test.jpg
   open test.jpg
   ```

3. 프론트엔드: http://localhost:3000/test-viewer
   - 이미지 자동으로 로드됨
   - 팬/줌 되는지 확인

---

## 문제 해결

### `ModuleNotFoundError: No module named 'pyvips'`
```bash
pip install pyvips minio
```

### `vips: unable to call ...`
```bash
brew install vips  # macOS
sudo apt-get install libvips-tools  # Ubuntu
```

### `S3Error: Bucket does not exist`
- MinIO 서버 켜기: `minio server ~/minio-data --console-address ":9001"`
- 스크립트가 자동으로 버킷 만들어줌

### 프론트엔드에 타일 안뜸
- 백엔드 켜졌는지 확인: `./gradlew bootRun`
- 브라우저 콘솔 에러 확인 (F12)
- imageId 맞는지 확인: 기본값은 `test-image-001`

---

## 생성되는 파일 구조

```
MinIO bucket: histoflow-tiles/
└── test-image-001/
    ├── image.dzi                    # DZI XML 메타데이터
    └── image_files/
        ├── 0/                       # 줌 레벨 0 (썸네일)
        │   └── 0_0.jpg
        ├── 1/                       # 줌 레벨 1
        │   ├── 0_0.jpg
        │   └── 1_0.jpg
        ├── 2/                       # 줌 레벨 2
        │   ├── 0_0.jpg
        │   ├── 1_0.jpg
        │   ├── 2_0.jpg
        │   └── 3_0.jpg
        └── ...                      # 더 많은 줌 레벨
```

---

## 설정값

- 타일 포맷: JPEG (85% 퀄리티)
- 타일 크기: 256×256 픽셀
- 타일 겹침: 0 픽셀 (겹침 없음)
- 피라미드 전략: 최하위 줌에서 타일 1개
- 정리: 업로드 후 로컬 타일 삭제 (디스크 공간 절약)

---

## 나중에 할 일

나중 스프린트에서 이 수동 스크립트를 기반으로:
- 이미지 업로드시 자동 타일 생성으로 교체
- Kotlin 백엔드 통합 또는 Python 마이크로서비스로
- 큰 이미지용 백그라운드 작업 처리
- 진행상황 추적 및 에러 처리

일단 Sprint 1 테스트용으로는 이거면 충분

---

## 왜 Python임?

- pyvips: 의료 이미지 처리할때 제일 좋음
- 검증된 솔루션: DZI 생성 업계 표준
- 일회성 설정: Sprint 1 테스트 데이터 만들 때만 씀
- 나중에 교체 가능: Kotlin이나 Python 서비스로 바꿀 수 있음

---

## 파일 목록

- `generate_test_tiles.py` - 메인 타일 생성 스크립트
- `setup.sh` - 환경 자동 설정
- `dev.sh` - 개발용 빠른 실행
- `requirements.txt` - Python 패키지 목록
- `.python-version` - pyenv Python 버전 (3.11.9)
- `QUICKSTART.ko.md` - 빠른 시작 가이드 (한글)
- `README.ko.md` - 이 파일
- `.gitignore` - venv, 테스트 이미지 제외

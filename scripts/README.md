# HistoFlow Development Scripts

전체 프로젝트 개발 환경 관리 스크립트

---

## dev-start.sh

전체 개발 환경 한번에 시작

### 실행
```bash
./scripts/dev-start.sh
```

### 하는 일
1. MinIO 체크 및 시작 (port 9000)
   - 안켜져있으면 새 터미널에서 시작
   - Console: http://localhost:9001

2. Backend 체크 및 시작 (port 8080)
   - 안켜져있으면 새 터미널에서 Gradle bootRun
   - API: http://localhost:8080

3. Frontend 체크 및 시작 (port 3000)
   - 안켜져있으면 새 터미널에서 npm run dev
   - App: http://localhost:3000

4. 브라우저 자동 오픈
   - 모든 서비스 실행되면 http://localhost:3000 자동으로 열림

### 특징
- 이미 실행중인 서비스는 스킵
- 각 서비스 새 터미널 창에서 실행
- 시작 확인 후 상태 표시
- 색상 코드로 상태 표시 (녹색=실행중, 노란색=시작중, 빨간색=실패)

### 주의사항
- macOS 전용 (osascript 사용)
- 각 서비스는 독립된 터미널에서 실행됨
- 종료하려면 각 터미널 창 닫거나 Ctrl+C

---

## api-smoke-test.sh

백엔드 API 핵심 엔드포인트 상태 확인

### 실행
```bash
./scripts/api-smoke-test.sh
# or override defaults
IMAGE_ID=my-slide API_BASE_URL=http://localhost:8080 ./scripts/api-smoke-test.sh
```

### 하는 일
1. `GET /actuator/health`
2. `GET /api/v1/tiles/<imageId>/image.dzi`
3. `GET /api/v1/tiles/<imageId>/image_files/0/0_0.jpg`

각 요청이 200 계열로 응답하면 ✅, 실패시 종료 코드 1을 반환함.

---

## 디렉토리 구조

```
HistoFlow/
├── scripts/              # 전체 프로젝트 스크립트 (여기)
│   ├── dev-start.sh     # 전체 개발 환경 시작
│   └── api-smoke-test.sh# API 상태 확인
│
├── backend/
│   └── scripts/         # 백엔드 전용 스크립트
│       ├── setup.sh     # Python 환경 설정
│       ├── dev.sh       # Python 가상환경 활성화
│       └── generate_test_tiles.py
│
└── frontend/
    └── (npm scripts in package.json)
```

---

## 사용 시나리오

### 처음 시작할 때
```bash
# 1. 전체 개발 환경 시작
./scripts/dev-start.sh

# 2. 타일 생성 필요하면
cd backend/scripts
./setup.sh              # 처음 한번만
./dev.sh                # Python 환경 활성화
python generate_test_tiles.py test.jpg test-image-001
```

### 매일 개발할 때
```bash
# 전체 환경 시작
./scripts/dev-start.sh

# 끝
```

---

## Troubleshooting

### "osascript: command not found"
수동으로 각 서비스 시작. 그런데 이건 리눅스에서 나는 에러라 아마 상관 없들듯:
```bash
# Terminal 1
minio server ~/minio-data --console-address ":9001"

# Terminal 2
cd backend && ./gradlew bootRun

# Terminal 3
cd frontend && npm run dev
```

### Port already in use
```bash
# 포트 사용중인 프로세스 찾기
lsof -i :9000   # MinIO
lsof -i :8080   # Backend
lsof -i :3000   # Frontend

kill -9 <PID>
```

### Service start failed
각 터미널 창에서 에러 메시지 확인
- MinIO: 데이터 디렉토리 권한 확인
- Backend: Gradle 빌드 에러 확인
- Frontend: npm install 했는지 확인

---

## 추가할 것들

- `dev-stop.sh`: 모든 서비스 종료
- `dev-status.sh`: 현재 실행 상태 확인

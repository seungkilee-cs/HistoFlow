# HistoFlow Architecture Overview

> Last Updated: 2026-02-21

HistoFlow is a cancer detection AI platform for histopathology image analysis. It follows a microservices architecture with a Kotlin/Spring Boot backend orchestrating Python ML services, backed by MinIO (S3-compatible) object storage and PostgreSQL.

---

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              User Browser                                  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  Frontend (React 19 + TypeScript + Vite)                             │  │
│  │  ├── HomePage ───────── drag-and-drop file upload                    │  │
│  │  ├── TileViewerPage ─── dataset browser + OpenSeadragon deep-zoom    │  │
│  │  └── upload.ts ──────── resumable multipart upload client            │  │
│  └──────────────────┬───────────────┬───────────────────────────────────┘  │
│                     │               │                                      │
└─────────────────────│───────────────│──────────────────────────────────────┘
                      │               │
     Multipart upload │               │ Tile/DZI requests
     presigned URLs   │               │ Dataset listing
                      │               │
                      ▼               ▼
            ┌─────────────────────────────────┐
            │  Backend (Kotlin Spring Boot)   │  :8080
            │                                 │
            │  Controllers:                   │
            │  ├── UploadController            │  POST /api/v1/uploads/initiate
            │  ├── MultipartUploadController   │  POST /api/v1/uploads/multipart/*
            │  ├── TileController              │  GET  /api/v1/tiles/**
            │  └── HealthController            │  GET  /health
            │                                 │
            │  Services:                      │
            │  ├── UploadService ──── S3 multipart ops (presign, complete, abort)
            │  ├── TileService ────── fetch DZI/tiles from MinIO, list datasets
            │  └── TilingTriggerService ── POST to tiling service via RestTemplate
            │                                 │
            └────────┬──────────┬─────────────┘
                     │          │
    Synchronous HTTP │          │ S3 API (AWS SDK)
    POST /jobs/tile  │          │
                     │          │
                     ▼          ▼
  ┌──────────────────┐    ┌─────────────┐    ┌──────────────┐
  │  Tiling Service  │    │    MinIO     │    │  PostgreSQL  │
  │  (Python FastAPI)│    │ (S3-compat) │    │   (16-alpine)│
  │  :8000           │    │ :9000/:9001 │    │   :5432      │
  │                  │    │             │    │              │
  │  pyvips DZI gen  │◄──►│  Buckets:   │    │  Tables:     │
  │  background task │    │  ├── histoflow-tiles      │  ├── tiling_jobs
  │                  │    │  └── unprocessed-slides   │  └── (future)
  └──────────────────┘    └─────────────┘    └──────────────┘
                                ▲
                                │ MinIO Python SDK
                                │
          ┌─────────────────────┼───────────────────────┐
          │                     │                       │
  ┌───────┴──────────┐  ┌──────┴───────────┐           │
  │  sk-regression   │  │ justin-regression│           │
  │  (Python CLI)    │  │  (Python CLI)    │           │
  │                  │  │                  │           │
  │  DINOv2 + custom │  │  DINOv2 + joblib │           │
  │  regression head │  │  sklearn clf     │           │
  │  (SlideRegressor)│  │  (DinoV2Embedder)│           │
  └──────────────────┘  └──────────────────┘           │
```

---

## Component Details

### 1. Frontend (`frontend/`)

| Aspect | Detail |
|--------|--------|
| **Framework** | React 19 + TypeScript |
| **Build** | Vite 7 |
| **Styling** | SCSS modules |
| **Key Libraries** | OpenSeadragon (deep-zoom viewer), react-router-dom v7 |
| **Port** | `:5173` (dev) |

**Pages & Components:**

| File | Purpose |
|------|---------|
| `App.tsx` | Router with nav bar, two routes: `/` (Home) and `/tile-viewer` |
| `HomePage.tsx` | Drag-and-drop upload with progress bar using multipart presigned URLs |
| `TileViewerPage.tsx` | Dataset search/browse + OpenSeadragon viewer with type-ahead autocomplete |
| `ImageViewer.tsx` | OpenSeadragon wrapper, consumes DZI from backend tile API |
| `utils/upload.ts` | Resumable multipart upload client with retry, concurrency control, localStorage persistence |

---

### 2. Backend (`backend/`)

| Aspect | Detail |
|--------|--------|
| **Language** | Kotlin 1.9 on JDK 17 |
| **Framework** | Spring Boot 3.5 |
| **Build** | Gradle (Kotlin DSL) |
| **Database** | H2 (dev) / PostgreSQL 16 (Docker) via Spring Data JPA |
| **Object Storage** | MinIO via AWS S3 SDK v2 + MinIO Java SDK |
| **Port** | `:8080` |

**Package Structure:**

```
com.histoflow.backend/
├── config/
│   ├── MinioConfig.kt         ─ S3Client, S3Presigner, MinioClient beans
│   ├── MinioProperties.kt     ─ @ConfigurationProperties for MinIO
│   ├── TilingProperties.kt    ─ base-url + strategy for tiling service
│   ├── WebConfig.kt           ─ CORS (localhost:5173, localhost:3000)
│   ├── RestClientConfig.kt    ─ RestClient bean
│   └── RestTemplateConfig.kt  ─ RestTemplate bean
├── controller/
│   ├── UploadController.kt         ─ presigned URL upload (single file)
│   ├── MultipartUploadController.kt─ multipart upload (initiate/presign/complete/abort)
│   ├── TileController.kt           ─ DZI/tile serving, dataset listing, tiling status
│   └── HealthController.kt         ─ health check
├── service/
│   ├── UploadService.kt             ─ S3 multipart operations
│   ├── TileService.kt               ─ fetch tiles/DZI from MinIO, dataset aggregation
│   └── TilingTriggerService.kt      ─ HTTP POST to tiling service
├── domain/tiling/
│   ├── TilingJobEntity.kt           ─ JPA entity
│   └── TilingJobStatus.kt           ─ enum
├── dto/tiling/
│   └── TilingJobStatusResponse.kt   ─ response DTO
└── repository/tiling/
    └── TilingJobRepository.kt       ─ Spring Data repo
```

---

### 3. Tiling Service (`services/tiling/`)

| Aspect | Detail |
|--------|--------|
| **Language** | Python 3.12 |
| **Framework** | FastAPI + Uvicorn |
| **Image Processing** | pyvips (DZI tile generation) |
| **Storage** | MinIO Python SDK |
| **Port** | `:8000` |

**Endpoints:**

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/jobs/tile-image` | Accept tiling job, process in background |
| `GET` | `/health` | Health check |

**Processing pipeline:** Download source image from MinIO → generate DZI tiles with pyvips (256px, Q=85 JPEG) → upload tiles + `metadata.json` to MinIO `histoflow-tiles` bucket → cleanup temp files.

---

### 4. ML Services (`services/`)

#### sk-regression (`services/sk-regression/`)

| Aspect | Detail |
|--------|--------|
| **Type** | CLI-only (no HTTP API) |
| **Model** | Custom `SlideRegressor` with DINOv2 backbone (`facebook/dinov2-base`) |
| **Interface** | `docker compose exec sk-regression python -m src.main --model ... --images ...` |
| **Output** | JSON with classification label, probabilities, regression score |
| **MinIO** | Downloads images via `s3://` or `minio://` URIs |

#### justin-regression (`services/justin-regression/`)

| Aspect | Detail |
|--------|--------|
| **Type** | CLI-only (no HTTP API) |
| **Model** | DINOv2 embedder + sklearn classifier (loaded via `joblib`) |
| **Interface** | Same CLI pattern as sk-regression |
| **MinIO** | Identical `minio_io.py` module (code duplication) |

---

### 5. Infrastructure (`docker/`)

**Docker Compose files:**

| File | Purpose |
|------|---------|
| `docker-compose.base.yml` | Core services: Postgres, MinIO, backend, tiling |
| `docker-compose.dev.yml` | Dev profile: expose ports to host |
| `docker-compose.ci.yml` | CI profile overrides |
| `docker-compose.ml.yml` | ML services: sk-regression (CPU profile) |

**Storage Buckets:**

| Bucket | Purpose |
|--------|---------|
| `unprocessed-slides` | Raw uploaded WSI files |
| `histoflow-tiles` | Generated DZI tiles + metadata |

---

## Data Flow

### Upload → Tiling Pipeline

```
1. Frontend: User drops file
2. Frontend → Backend:  POST /api/v1/uploads/multipart/initiate
                        ↳ Returns {uploadId, key, partSize}
3. Frontend → Backend:  POST /api/v1/uploads/multipart/presign
                        ↳ Returns presigned S3 URLs for parts
4. Frontend → MinIO:    PUT presigned-url (×N parts, 4 concurrent workers)
5. Frontend → Backend:  POST /api/v1/uploads/multipart/complete
                        ↳ Completes S3 multipart upload
                        ↳ Triggers tiling via HTTP POST
6. Backend → Tiling:    POST /jobs/tile-image {image_id, source_bucket, ...}
7. Tiling service:      Downloads from MinIO → pyvips DZI → uploads tiles to MinIO
8. Frontend:            Polls GET /api/v1/tiles/{imageId}/status
                        ↳ Backend checks MinIO for image.dzi existence
```

### Tile Viewing Flow

```
1. Frontend:  GET /api/v1/tiles/datasets?limit=5
              ↳ Backend aggregates tile bucket objects by imageId prefix
2. Frontend:  GET /api/v1/tiles/{imageId}/image.dzi
              ↳ Backend streams DZI XML from MinIO
3. OpenSeadragon:  GET /api/v1/tiles/{imageId}/image_files/{level}/{x}_{y}.jpg
              ↳ Backend streams individual tile JPEGs from MinIO
```

### ML Inference Flow (Current: Manual CLI)

```
1. User: docker compose exec sk-regression python -m src.main \
           --model models/head.pkl \
           --images s3://histoflow-tiles/{imageId}/image_files/...
2. sk-regression: Downloads tile images from MinIO
3. sk-regression: DINOv2 embedding → regression head → classification
4. Output: JSON results printed to stdout
```

> **Note:** ML services currently have NO HTTP API and are NOT integrated into the automated pipeline. They require manual invocation via `docker compose exec`.

---

## Technology Stack Summary

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, TypeScript, Vite 7, OpenSeadragon 5, SCSS |
| Backend | Kotlin 1.9, Spring Boot 3.5, Gradle, JPA/Hibernate |
| Tiling | Python 3.12, FastAPI, pyvips, MinIO SDK |
| ML | Python, PyTorch, DINOv2, scikit-learn, joblib |
| Object Storage | MinIO (S3-compatible) |
| Database | PostgreSQL 16 / H2 (dev fallback) |
| Containerization | Docker, Docker Compose (multi-file, profiles) |

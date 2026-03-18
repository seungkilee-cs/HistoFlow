# Refactoring Plan & Monorepo Consolidation

> Last Updated: 2026-02-21

This document identifies concrete refactoring opportunities and proposes a monorepo structure to simplify development, testing, and deployment of the HistoFlow platform.

---

## Part 1: Refactoring Opportunities

### 1. Duplicated MinIO I/O Code

**Problem:** `sk-regression/src/minio_io.py` and `justin-regression/src/minio_io.py` are nearly identical (~95% overlap). Both contain:
- `MinioConfig` dataclass
- `parse_uri()` function
- `download_to_temp()` function
- `cleanup_temp()` function

**Fix:** Extract into a shared Python package `histoflow-common` or `histoflow-ml-common`:

```
packages/
  ml-common/
    histoflow_ml_common/
      __init__.py
      minio_io.py      ← single source of truth
      config.py         ← shared settings pattern
    pyproject.toml
```

Both services import from it:
```python
from histoflow_ml_common.minio_io import MinioConfig, download_to_temp
```

---

### 2. ML Services Have No HTTP API

**Problem:** Both `sk-regression` and `justin-regression` are CLI-only tools. They cannot:
- Be triggered automatically after tiling completes
- Report results back to the backend
- Be health-checked by Docker/orchestrator

**Fix:** Add a minimal FastAPI wrapper to each ML service (or better, a unified ML gateway):

```python
# services/ml-gateway/src/main.py
@app.post("/predict")
async def predict(request: PredictRequest, background_tasks: BackgroundTasks):
    background_tasks.add_task(run_inference, request)
    return {"status": "accepted", "job_id": request.image_id}
```

Or, if adopting RabbitMQ (see [message-queue-proposal.md](./message-queue-proposal.md)), add queue consumers instead.

---

### 3. Hardcoded Backend URL in Frontend

**Problem:** `ImageViewer.tsx` has a hardcoded `http://localhost:8080`:

```typescript
tileSources: `http://localhost:8080/api/v1/tiles/${imageId}/image.dzi`,
```

While `TileViewerPage.tsx` and `upload.ts` correctly use:
```typescript
const API_BASE_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080';
```

**Fix:** Use the environment variable consistently in `ImageViewer.tsx`.

---

### 4. Two Upload Controllers with Overlapping Concerns

**Problem:** Both `UploadController.kt` and `MultipartUploadController.kt` handle uploads and both trigger tiling via `TilingTriggerService`. The single-file upload path generates presigned URLs via the MinIO Java SDK while the multipart path uses the AWS S3 SDK — two different SDKs for the same object storage.

**Fix:**
- Deprecate the single-file `UploadController` (presigned PUT) — all production uploads are multipart
- Consolidate to a single `UploadController` with multipart-only flow
- Standardize on AWS S3 SDK (already used by `UploadService`)

---

### 5. Dual S3 SDK Usage in Backend

**Problem:** The backend uses **both** the MinIO Java SDK (`io.minio:minio`) and the AWS S3 SDK (`software.amazon.awssdk:s3`) for the same MinIO instance:

| SDK | Used By |
|-----|---------|
| MinIO Java SDK | `UploadController` (presigned URLs, bucket ops) |
| AWS S3 SDK | `UploadService` (multipart upload), `TileService` (object retrieval), `MinioConfig` (S3Client, S3Presigner) |

**Fix:** Standardize on the AWS S3 SDK. Remove the MinIO Java SDK dependency. Migrate presigned URL generation from `MinioClient.getPresignedObjectUrl()` to `S3Presigner`.

---

### 6. Dataset Listing is Inefficient

**Problem:** `TileService.listDatasets()` aggregates datasets by scanning entire S3 bucket contents with `listObjectsV2` (fetching 1000 objects at a time) and grouping by prefix. This is O(n) on total objects.

**Fix:**
- Store dataset metadata in PostgreSQL when tiling completes (a `datasets` table)
- Query the database for listings instead of scanning S3
- Fall back to S3 scan for legacy/unindexed datasets

---

### 7. No Consistent Error Handling

**Problem:** Backend controllers mix exception handling styles:
- `UploadController` catches exceptions and returns error DTOs
- `MultipartUploadController` catches exceptions then calls `ex.printStackTrace()` and returns bare 500s
- No `@ControllerAdvice` global exception handler

**Fix:** Add a `GlobalExceptionHandler` with `@ControllerAdvice` to centralize error responses:

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleNotFound(e: IllegalArgumentException) = ResponseEntity.notFound().build()

    @ExceptionHandler(IllegalStateException::class)
    fun handleServiceError(e: IllegalStateException) =
        ResponseEntity.status(502).body(ErrorResponse(e.message))
}
```

---

### 8. Tiling Status Tracking

**Problem:** The backend has JPA entities for `TilingJobEntity` and `TilingJobStatus` but they appear to be unused. Tiling status is checked by probing MinIO for `image.dzi` existence.

**Fix:** Wire up the existing JPA entities:
1. Create a `TilingJobEntity` record when triggering tiling
2. Tiling service reports completion back (via HTTP callback or message queue)
3. Backend updates the entity status
4. Status endpoint queries the database instead of probing S3

---

## Part 2: Monorepo Setup Proposal

### Current Structure (Problems)

```
HistoFlow/
├── backend/          ← Gradle project (standalone)
├── frontend/         ← npm/Vite project (standalone)
├── services/
│   ├── tiling/       ← Python project (standalone Dockerfile + requirements.txt)
│   ├── sk-regression/← Python project (standalone Dockerfile + requirements.txt)
│   └── justin-regression/← Python project (standalone)
├── docker/           ← compose files
└── scripts/          ← dev helper scripts
```

**Problems:**
- No shared dependencies between Python services (duplicated code)
- No unified test runner (`./gradlew test` only runs backend tests)
- No single build command
- Docker compose files reference `../backend`, `../services/tiling` with relative paths
- Each Python service manages its own `requirements.txt` independently

### Proposed Monorepo Structure

```
HistoFlow/
├── apps/
│   ├── backend/          ← Kotlin Spring Boot (Gradle submodule)
│   │   ├── build.gradle.kts
│   │   ├── Dockerfile
│   │   └── src/
│   ├── frontend/         ← React/TS (npm workspace)
│   │   ├── package.json
│   │   ├── Dockerfile
│   │   └── src/
│   ├── tiling/           ← Python FastAPI
│   │   ├── Dockerfile
│   │   ├── pyproject.toml
│   │   └── src/
│   └── ml-gateway/       ← Unified ML service (consolidates sk + justin)
│       ├── Dockerfile
│       ├── pyproject.toml
│       └── src/
├── packages/
│   └── ml-common/        ← Shared Python library
│       ├── pyproject.toml
│       └── histoflow_ml_common/
│           ├── __init__.py
│           ├── minio_io.py
│           ├── config.py
│           └── embedders.py
├── docker/
│   ├── docker-compose.yml          ← single unified compose
│   ├── docker-compose.dev.yml
│   └── docker-compose.test.yml
├── docs/
├── scripts/
│   ├── dev-start.sh
│   ├── test-all.sh
│   └── lint-all.sh
├── Makefile                         ← unified commands
├── package.json                     ← npm workspace root
├── settings.gradle.kts              ← Gradle multi-project root
└── pyproject.toml                   ← Python workspace (uv/hatch)
```

### Key Benefits

| Benefit | Details |
|---------|---------|
| **Shared ML code** | `packages/ml-common` eliminates `minio_io.py` duplication |
| **Unified commands** | `make test`, `make build`, `make dev` across all services |
| **Atomic changes** | Cross-service changes in a single PR |
| **Simpler Docker** | Compose files reference `./apps/backend` instead of `../backend` |
| **Consistent tooling** | Shared linting, formatting, and CI configs |
| **Easier onboarding** | One `git clone`, one `make dev`, everything runs |

### Tool Recommendations

| Concern | Current | Proposed |
|---------|---------|----------|
| **Python dependency management** | `requirements.txt` per service | `uv` workspace with `pyproject.toml` |
| **Python package sharing** | Copy-paste | `uv` workspace local packages |
| **Monorepo task runner** | `scripts/dev-start.sh` | `Makefile` + `docker compose` profiles |
| **Multi-service dev** | Manual terminal management | `docker compose --profile dev up` |
| **Testing** | Manual per-service | `make test` → runs Gradle, pytest, vitest |

### Makefile Example

```makefile
.PHONY: dev test build lint

dev:
	docker compose -f docker/docker-compose.yml \
	               -f docker/docker-compose.dev.yml \
	               --profile dev up --build

test:
	cd apps/backend && ./gradlew test
	cd apps/frontend && npm test -- --run
	cd apps/tiling && python -m pytest tests/
	cd apps/ml-gateway && python -m pytest tests/

build:
	docker compose -f docker/docker-compose.yml build

lint:
	cd apps/backend && ./gradlew ktlintCheck
	cd apps/frontend && npm run lint
	cd apps/tiling && ruff check src/
	cd apps/ml-gateway && ruff check src/
```

---

### Migration Path

| Step | Action | Risk |
|------|--------|------|
| 1 | Create `packages/ml-common/` with extracted shared code | Low |
| 2 | Add `pyproject.toml` to all Python services | Low |
| 3 | Point ML services at shared package via `pip install -e ../packages/ml-common` | Low |
| 4 | Move services into `apps/` directory | Medium (update CI, compose paths) |
| 5 | Add root `Makefile` | Low |
| 6 | Consolidate `sk-regression` + `justin-regression` into `ml-gateway` | Medium |
| 7 | Update Docker compose to reference new paths | Medium |
| 8 | Add `npm workspaces` root for frontend | Low |

> [!IMPORTANT]
> Steps 1-3 can be done incrementally without breaking anything. Steps 4-7 should be done together in a single PR to keep compose and CI in sync.

---

## Priority Matrix

| Refactoring Item | Impact | Effort | Priority |
|-----------------|--------|--------|----------|
| Fix hardcoded URL in ImageViewer | High (breaks deploy) | 5 min | **P0** |
| Extract shared `minio_io.py` | Medium (tech debt) | 1 hour | **P1** |
| Add `@ControllerAdvice` error handling | Medium (reliability) | 2 hours | **P1** |
| Consolidate upload controllers | Medium (simplicity) | 3 hours | **P2** |
| Standardize on one S3 SDK | Medium (maintenance) | 4 hours | **P2** |
| Add HTTP/queue API to ML services | High (enables automation) | 1 day | **P2** |
| Database-backed dataset listing | Medium (performance) | 4 hours | **P2** |
| Wire up TilingJobEntity | Medium (observability) | 3 hours | **P3** |
| Full monorepo restructure | High (DX) | 2 days | **P3** |

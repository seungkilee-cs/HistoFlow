# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What HistoFlow is

A digital-pathology cancer-detection platform (inspired by Lunit). A whole-slide image (WSI) is uploaded, tiled into a Deep Zoom pyramid, analyzed by a DINOv2-based classifier that produces tile-level tumor predictions + a heatmap, and viewed in the browser with OpenSeadragon. It is a polyglot monorepo: a Kotlin Spring Boot orchestrator, a React/Vite frontend, and several Python FastAPI/CLI ML services, wired together with PostgreSQL and MinIO (S3-compatible object storage).

## Pipeline (the big picture)

The backend is an **orchestrator**, not a worker. Heavy CPU/GPU work lives in the Python services; the backend triggers jobs over HTTP and the services call back into `/api/v1/internal/...` endpoints to report progress.

```
upload (browser → presigned PUT → MinIO:unprocessed-slides)
  └─ backend.TilingTriggerService → POST tiling:/jobs/tile-image
       └─ tiling svc writes DZI tiles + manifest → MinIO:histoflow-tiles
            └─ progress → POST backend:/api/v1/internal/tiling/jobs/{id}/events
       └─ frontend tracks via SSE: GET /api/v1/tiling/events/stream
  └─ user triggers analysis → backend.AnalysisService → POST region-detector:/jobs/analyze
       └─ region-detector reads tiles, runs DINOv2 + classifier, writes
          heatmap PNG / tile-predictions JSON / summary JSON → MinIO
            └─ progress → POST backend:/api/v1/internal/analysis/jobs/{id}/events
  └─ view: OpenSeadragon loads /api/v1/tiles/{imageId}/image.dzi;
     heatmap + red tumor-box overlays loaded from analysis results
```

**Persistence split (a core design rule):** queryable summaries (job status, parameters, summary metrics, artifact *keys*) live in Postgres; heavy artifacts (tiles, heatmaps, tile-prediction blobs) live in MinIO and are streamed/presigned on demand — never inlined into list responses. Completed analysis runs are treated as immutable history. See `docs/analysis-persistence-and-backend-optimization.md` (the design driving the current backend work).

## Component map

| Tier | Path | Stack | Role |
|------|------|-------|------|
| Backend | `backend/` | Kotlin 1.9 / Spring Boot 3.5 / JDK 17 | REST API, job orchestration, Postgres + MinIO gateway |
| Frontend | `frontend/` | React 19 / Vite 7 / TS 5.9 | Upload UI, dataset browser, OpenSeadragon viewer + overlays |
| Tiling | `services/tiling/` | FastAPI + pyvips | WSI → DZI tile pyramid (port 8000) |
| Region detector | `services/region-detector/` | FastAPI + PyTorch/DINOv2 | tumor classification + heatmap (port 8001) |
| sk-regression | `services/sk-regression/` | CLI (ResNet18 + LR) | offline model training/inference, not a server |
| justin-regression | `services/justin-regression/` | CLI (DINOv2 + LR/SVM) | trains the classifier `region-detector` serves |
| Orchestration | `docker/` | Docker Compose | layered compose files + Postgres init |

### Backend internals (`backend/src/main/kotlin/com/histoflow/backend/`)
Layered: `controller/` (REST) → `service/` (logic) → `repository/` (Spring Data JPA) → `domain/{tiling,analysis}/` (entities + status/stage enums). `config/` holds `@ConfigurationProperties` (`MinioProperties`/`minio.*`, `TilingProperties`/`tiling.*`, `AnalysisProperties`/`analysis.*`), the S3/MinIO clients, and `MinioBucketInitializer` (creates buckets at startup when `minio.initialize-buckets=true`). `AnalysisService` is the largest/most central service. Tables: `tiling_jobs`, `analysis_jobs`, `saved_analyses`.

### Frontend internals (`frontend/src/`)
`App.tsx` routes `/` (HomePage = upload + job list) and `/tile-viewer/:imageId?` (TileViewerPage = dataset search + analysis + viewer). `jobs/JobsContext.tsx` (Context + useReducer) tracks upload/tiling job lifecycle, subscribing to backend SSE with a polling fallback. `components/ImageViewer.tsx` wraps OpenSeadragon and renders the heatmap as a pixelated overlay plus per-tile red prediction boxes. API base URL is `import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080'`.

## Common commands

### Full stack (recommended for manual / e2e testing)
```bash
./dev.sh            # base stack: postgres, minio, backend, tiling, frontend
./dev.sh --ml       # also start region-detector (DINOv2; first boot ~2-3 min)
./dev.sh --down     # stop & remove all docker services
```
`dev.sh` auto-resolves free host ports (see "Ports" below), brings up Docker, waits on health checks, then runs the Vite dev server in the foreground with `VITE_BACKEND_URL` pointed at the resolved backend port. `scripts/setup-and-start.sh` is an alternative that additionally validates the model file and runs region-detector tests.

### Backend (`cd backend`)
```bash
./gradlew build                 # compile + test
./gradlew test                  # JUnit 5 tests (uses in-memory H2)
./gradlew test --tests "com.histoflow.backend.controller.AnalysisControllerTest"   # single test class
./gradlew bootRun               # run locally (needs Postgres + MinIO reachable)
```
Controller tests use `@WebMvcTest` with mocked services; the test profile (`src/test/resources/application.yml`) uses H2 in PostgreSQL mode with `ddl-auto: create-drop` and `minio.initialize-buckets: false`.

### Frontend (`cd frontend`)
```bash
npm install
npm run dev                     # Vite dev server
npm run build                   # tsc typecheck + vite build
npm run lint                    # eslint over src/**/*.{ts,tsx}
npm test                        # vitest (watch); add `run <file>` for a single run
npx vitest run src/pages/TileViewerPage.test.tsx   # one test file
```

### Python services
Each service has its own `requirements.txt` and `Dockerfile`; normally run via Compose. region-detector has unit tests:
```bash
cd services/region-detector && pytest tests -q
```

## Things that will trip you up

- **Canonical backend source is `backend/src/`** (a single Gradle module). `settings.gradle.kts` also declares `apps:api` and `libs:common`, but those have **no build files and no real code** — they are vestigial scaffolding. The Dockerfile and the Spring Boot build use `backend/src/` only; ignore `backend/apps/` / `backend/libs/`.
- **Ports are dynamic.** `scripts/lib/runtime-ports.sh` probes for free ports (backend prefers 8080, tiling 8000, analysis 8001, minio 9000/9001, frontend 5173) and writes the chosen values to `.omx/runtime/ports.env`, which Compose consumes via `--env-file`. Don't assume a hardcoded port — read that file or the `dev.sh` startup banner.
- **`--ml` requires a model file** at `services/justin-regression/models/dinov2_classifier.pkl`. The `region-detector` container mounts that directory as `/app/models`, so the regression service is what *produces* the model the analysis server *serves*.
- **MinIO has two buckets**: `unprocessed-slides` (raw uploads) and `histoflow-tiles` (tiles + DZI + analysis artifacts). The backend distinguishes `minio.endpoint` (internal, container network) from `minio.public-endpoint` (what presigned URLs hand to the browser).
- **Frontend uses native `fetch`**, not the `axios` dependency that's in `package.json`.
- **Health endpoints are not uniform**: backend `/api/v1/health`, tiling & region-detector `/health`, MinIO `/minio/health/live`.
- **Compose is layered**: `docker-compose.base.yml` (no ports/profiles) + `.dev.yml` (`dev` profile, host ports) + `.ml.yml` (`cpu` profile, ML services). `.ci.yml` is the headless variant.
- **Stale docs**: `README.md` points at a non-existent `scripts/dev-start.sh` (use `./dev.sh`); `docker/README.md` references `scripts/sprint2/docker/` (actual path is `scripts/docker/`); the `.github/workflows/*.yml` files are empty placeholders (no CI is wired up yet).

## Privacy note

This is patient-adjacent medical imaging software. When adding patient-linked storage, follow the access-control / audit / encryption guidance in `docs/analysis-persistence-and-backend-optimization.md` (HIPAA / FDA SaMD context).

# Docker Orchestration Overview

This directory leaves service-specific Docker assets (e.g., `backend/Dockerfile`, `services/tiling/Dockerfile`) in their respective projects, while centralizing environment-level orchestration here. Keeping the Compose configs in one place makes it easy to spin up the full HistoFlow stack—MinIO, Kotlin backend, and Python tiling service—without duplicating setup logic across subprojects.

## Why a top-level `docker/` directory?

- Cross-service coordination: Each microservice owns its build instructions, but shared wiring (network aliases, ports, volumes) belongs to the application as a whole.
- Consistent tooling: Compose files are reused by dev scripts and CI jobs. Having a single source of truth prevents divergent copies under `backend/` or `services/tiling/`.
- Profile separation: Multiple usage modes (developer laptop vs. CI runner) live side-by-side without polluting service directories.

## Files

| File | Purpose |
| --- | --- |
| `docker-compose.base.yml` | Core service definitions: MinIO, backend, tiling. No host ports or profiles so it can be reused in any environment. |
| `docker-compose.dev.yml` | Development overlay (`dev` profile) that publishes ports 9000/9001, 8080, 8000 for localhost access. |
| `docker-compose.ci.yml` | CI overlay (`ci` profile) tuned for headless automation. |
| `minio.env` | Shared environment file providing the default MinIO root user and password. |

Service Dockerfiles remain close to their code:

- Backend runtime: `backend/Dockerfile` (multi-stage Gradle build).
- Python tiling service: `services/tiling/Dockerfile`.

## Usage

### Development stack

Bring up the full stack with the helper script (wraps base + dev overlays):

```bash
./scripts/sprint2/docker/start-stack.sh
```

This creates the `histoflow-sprint2` compose project, builds images if needed, and exposes services on:

- MinIO: `http://localhost:9000` (console `:9001`)
- Backend: `http://localhost:8080`
- Tiling service: `http://localhost:8000`

Tear everything down (including volumes):

```bash
./scripts/sprint2/docker/stop-stack.sh
```

Run the automated end-to-end test (upload + tiling + verification) inside the stack:

```bash
./scripts/sprint2/docker/e2e-compose-test.sh ~/slides/sample.svs
```

### Direct compose commands

You can also invoke Compose manually:

```bash
# Dev profile (with host ports)
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.dev.yml \
  --profile dev up --build

# Stop and remove
# (add --profile dev to ensure only dev services are touched)
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.dev.yml \
  --profile dev down -v
```

### CI profile

In CI, use the `ci` profile to run services without exposing ports externally:

```bash
docker compose \
  -f docker/docker-compose.base.yml \
  -f docker/docker-compose.ci.yml \
  --profile ci up --build --abort-on-container-exit
```

Integration tests can reach services via their Compose service names (`http://backend:8080`, `http://tiling:8000`, `http://minio:9000`). Override URLs for existing scripts by exporting `BACKEND_URL`, `TILING_URL`, or `MINIO_URL`.

## Environment variables

`minio.env` is consumed by both dev and CI overlays. Update it if you rotate credentials; scripts and Dockerfiles reference the same keys (`MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`).

## Extending the stack

- Add new services by editing `docker-compose.base.yml`, then expose developer-friendly settings in `docker-compose.dev.yml`.
- For additional profiles (e.g., staging), create another overlay file and reuse the base definitions.
- Keep build-specific Dockerfiles in their respective project directories to maintain clear ownership and incremental build performance.

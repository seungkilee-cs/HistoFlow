# HistoFlow Enhancement Roadmap

This roadmap turns a working end-to-end prototype (upload → tile → DINOv2 analysis → view) into a
reproducible, observable, secure ML serving platform. It is the plan of record behind the
[engineering dev log](./DEVLOG.md).

Each row is an **independently reviewable PR**. Branches are siblings off the active development
branch and touch non-overlapping files, so they can be opened, reviewed, and merged in any order
except where a dependency is noted. Sizes: **S** ≈ <1 day · **M** ≈ 1–3 days · **L** ≈ multi-day.

**Capability tags** — what each PR demonstrates for portfolio purposes:

- 🏗️ **AI DevOps / Infra** — CI, secrets, containers, queues, observability, serving topology
- 🤖 **AI Engineering / MLOps** — model versioning, reproducible artifacts, inference serving, ML pipeline
- ⚙️ **Backend** — Spring Boot orchestration, persistence, correctness
- 🖥️ **Frontend** — React/TypeScript correctness & performance
- 🎨 **UX / a11y** — workflow feedback, accessibility, responsive design

The roadmap is the output of a four-lane audit (backend, frontend, Python ML services, and
system-level architecture/security). Findings and rationale live in [`DEVLOG.md`](./DEVLOG.md);
the source design intent lives in
[`analysis-persistence-and-backend-optimization.md`](./analysis-persistence-and-backend-optimization.md).

---

## Epic A — Foundation & CI
*Cheap, safe, and unblocks regression evidence for everything after it.*

| PR | Tag | Scope | Size | Depends on |
|----|-----|-------|------|------------|
| 1 | 🏗️ | **Repo hygiene & docs.** Fix stale docs (`README` `dev-start`→`dev.sh`; `docker/README` `sprint2`→`docker`), fix the broken `e2e-compose-test.sh` `REPO_ROOT`, drop the vestigial `apps:api`/`libs:common` Gradle includes + the stray `backend/apps` copy, prune `archive/`, pin Python dependencies (incl. `matplotlib<3.9`). | S | — |
| 2 | 🏗️ | **Wire CI.** Backend `./gradlew test`, frontend `tsc`+`vitest`, region-detector `pytest`, add region-detector to the CI compose overlay, populate `security.yml` (dependency + image scan). | M | 1 |

## Epic B — Critical fixes
*Small, independent, ship immediately.*

| PR | Tag | Scope | Size | Depends on |
|----|-----|-------|------|------------|
| 3 | 🤖 | **Matplotlib ≥3.9 break.** `cm.get_cmap` was removed in 3.9; the heatmap stage fails on every analysis on a clean install. Switch to `matplotlib.colormaps[...]`. | S | — |
| 4 | ⚙️ | **Tile-serving crash + global error handling.** Defensive tile-coordinate parse (no 500 on malformed input) and a `@RestControllerAdvice` global handler; remove `printStackTrace`. | S | — |

## Epic C — Security foundation
*This is patient-adjacent medical imaging; today there is no auth at all.*

| PR | Tag | Scope | Size | Depends on |
|----|-----|-------|------|------------|
| 5 | 🏗️ | **Externalize secrets.** Env placeholders in `application.yml`/compose, wire `.env`, fail-fast in non-dev profiles. | S | — |
| 6 | 🔒 | **Internal-callback auth.** Shared-secret `X-Internal-Token` filter on `/api/v1/internal/**` + matching header in both Python callers; move `/internal` off the public CORS prefix. Closes the "any caller can forge cancer scores" hole. | S | — |
| 7 | 🔒 | **Scope SSE emitters** by `imageId`/owner — fixes cross-user job disclosure. | S | — |
| 8 | 🔒 | **Public API authN/Z.** JWT/OIDC resource server + tenant-claim scaffold (ship audit-only, then enforce). | L | 5 |

## Epic D — Worker durability & back-pressure
*The headline platform story: make the serving layer survive load and restarts.*

| PR | Tag | Scope | Size | Depends on |
|----|-----|-------|------|------------|
| 9 | ⚙️ | **HTTP timeouts + async dispatch.** Bounded executor, return `202`; stops request-thread exhaustion when a service wedges. | M | — |
| 10 | ⚙️ | **Optimistic locking.** `@Version` + `@Transactional` on `updateJob` paths (lost-update race between progress + completion callbacks). | S | — |
| 11 | 🤖 | **Bounded inference concurrency.** Semaphore/queue in the Python services + a forward-pass lock on the shared model (OOM + thread-safety). | M | — |
| 12 | 🤖 | **Callback resilience.** Retry/backoff + idempotency key; reconstruct job state from MinIO on restart; TTL-evict the in-memory job map. | M | — |
| 13 | ⚙️ | **Stale-job reaper.** `@Scheduled` sweep that fails `PROCESSING` rows orphaned past a TTL. | S | 9–12 |

## Epic E — Serving performance
| PR | Tag | Scope | Size | Depends on |
|----|-----|-------|------|------------|
| 14 | ⚙️ | **Stream heavy results.** Default `includeTilePredictions=false`; stream/paginate predictions; cache immutable summaries. | M | — |
| 15 | 🏗️ | **Presigned tile/heatmap GET.** Offload the byte-proxy off the JVM; let browsers fetch MinIO directly. | M | — |
| 16 | ⚙️ | **Kill the dataset bucket scan.** Persist dataset metadata (Slide-lite table) instead of scanning all tiles per request. | M | 28 |
| 17 | 🤖 | **Inference perf.** MPS/CUDA device selection, `inference_mode`, autocast, device-tuned batch size. | S | — |
| 18 | 🤖 | **Heatmap robustness.** Memory cap + dimension validation + vectorized fallback; temp-dir namespacing; tile-download retry. | M | — |

## Epic F — Frontend correctness & performance
| PR | Tag | Scope | Size | Depends on |
|----|-----|-------|------|------------|
| 19 | 🖥️ | **Shared typed API client.** One client, delete `axios` + dead example, central error normalization. | M | — |
| 20 | 🖥️ | **JobsContext hardening.** Guard SSE `JSON.parse`, ref-based reconcile interval (stop the poll stampede), split state/actions context + memo. | M | 19 |
| 21 | 🖥️ | **Viewer overlay performance.** Single canvas/SVG overlay or top-N cap, mutate-not-rebuild, debounced sliders, `React.lazy` OpenSeadragon. | M | — |
| 22 | 🖥️ | **Status normalization.** Fix the lowercase/uppercase mismatch that can cause an infinite analysis poll; type the analysis responses. | S | 19 |

## Epic G — UX, accessibility, validation
| PR | Tag | Scope | Size | Depends on |
|----|-----|-------|------|------------|
| 23 | 🎨 | **Long-task UX.** Indeterminate progress, elapsed time, cancel, viewer load skeleton, viewer error/empty states. | M | — |
| 24 | 🎨 | **Responsive sidebar drawer** — the viewer is currently unusable below 768px. | S | — |
| 25 | 🎨 | **a11y pass.** Slider `aria-valuetext`, combobox keyboard nav, drop-zone Space key, contrast, toast live-region. | M | — |
| 26 | ⚙️🖥️ | **Input validation.** Backend bean validation + object-key sanitization; Python Pydantic `Field` constraints; frontend upload guards. | M | — |
| 27 | 🤖 | **Python observability.** Structured `logging` + FastAPI `lifespan` migration. | S | — |

## Epic H — MLOps & data model
*The AI-engineering showcase: reproducibility and provenance.*

| PR | Tag | Scope | Size | Depends on |
|----|-----|-------|------|------------|
| 28 | 🗄️ | **Migrations.** Adopt Flyway/Liquibase; set `ddl-auto: validate`. Precondition for further schema work. | M | — |
| 29 | 🤖 | **Model versioning.** `ModelVersion` entity attached to every analysis run; record backbone revision + classifier checksum. | M | 28 |
| 30 | 🤖 | **Reproducible artifacts.** Pin DINOv2 to a commit SHA; bake + checksum the classifier into the image. | S | — |
| 31 | 🗄️ | **Domain model.** `Slide` → `Patient`/`Case` entities (doc-driven), denormalized `patientId`, tenant column. | L | 28, 8 |
| 32 | 🧠 | *(optional — model-understanding track)* **Eval harness.** Held-out metrics, calibration, DINOv2-vs-ResNet comparison report, confidence surfacing. | M | 29 |

---

## Recommended sequencing

Portfolio-optimal order that front-loads the reproducibility + durability + security narrative:

```
A(1→2) → B(3,4) → C(5,6,7) → H(30,28,29) → D(9–13) → E(14,15,17,18)
                                                        ↘ F & G interleave anytime
```

Epics **F** (frontend) and **G** (UX) are orthogonal to the platform work and can run in parallel.
PR 8 (full auth), PR 16 (dataset table), and PR 31 (domain model) are the heavier items and gate on
their dependencies above.

## Status

Progress is logged per-PR in [`DEVLOG.md`](./DEVLOG.md).

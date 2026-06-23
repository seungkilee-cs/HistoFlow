# HistoFlow — Engineering Dev Log

A running log of how I'm taking HistoFlow from a working prototype to a production-shaped
**ML serving platform**. The interesting work here is not the model — it's everything around it:
how a whole-slide image moves through ingestion, tiling, inference, and visualization reliably,
reproducibly, and safely.

## Thesis

HistoFlow already proves the *idea* end-to-end: upload a whole-slide image, tile it into a Deep Zoom
pyramid, run a DINOv2 + classifier over the tiles, and render a tumor-probability heatmap in the
browser. That's the easy 80%. This log is the hard 20% — the platform and infrastructure work that
separates "it runs on my laptop" from "it serves real cases":

- **Reproducibility** — pinned model artifacts, recorded model versions, deterministic builds.
- **Durability** — jobs that survive a restart, bounded concurrency, retries, dead-letter handling.
- **Observability** — structured logs, real readiness/liveness, regression-gating CI.
- **Security & compliance** — auth on a patient-adjacent system, secret management, audit.
- **Serving performance** — getting heavy artifacts off the request path and onto object storage.

The model itself is a deliberate, defensible *choice* (a self-supervised DINOv2 backbone generalizes
to histology better than an ImageNet CNN, with a light classical head for speed) — but the
engineering story is the platform that makes that model dependable. That's the capability this
project is meant to demonstrate: **AI engineering and ML infrastructure**, not model research.

> **How to read this log:** entries are chronological. Each one states the *problem*, the *change*,
> and — most importantly — *why it matters at the platform level*. The corresponding plan lives in
> [`ROADMAP.md`](./ROADMAP.md); each entry maps to one roadmap PR.

---

## Entry 0 — Baseline audit

**Context.** Before changing anything, I ran a four-lane review of the codebase — backend
(Kotlin/Spring), frontend (React/TS), the Python ML services, and a system-level
architecture/security pass — to build an evidence-based picture of what's solid and what's fragile.

**What I found.** The data foundation is genuinely good: analysis runs are effectively immutable,
history is indexed and paginated, summaries live in Postgres while heavy artifacts live in object
storage, buckets initialize at startup, and tiling concurrency is already configurable. Those are
the right instincts and they're already in place.

The gaps cluster in exactly the places a platform engineer is paid to care about:

1. **Security.** No authentication anywhere — and the internal callback endpoints that the Python
   services use to report results are unauthenticated, so any caller on the network could forge a
   cancer score or repoint a result artifact. On patient-adjacent imaging, that's the headline risk.
2. **Durability.** The worker job state lives in an in-memory dict on a single-replica service.
   A restart orphans in-flight jobs and the backend never learns. There's no retry on the result
   callback, no bounded concurrency, and the backend dispatches to the services synchronously on the
   request thread.
3. **Reproducibility.** The DINOv2 backbone is pulled live at runtime with no pinned revision, the
   classifier is bind-mounted rather than baked, and there's no record of which model produced which
   result.
4. **Regression safety.** Real tests exist (backend JUnit, frontend vitest, region-detector pytest)
   but the CI workflow files are empty placeholders — nothing runs them.
5. **A clean-install breakage.** The heatmap stage calls a Matplotlib API removed in 3.9, and the
   dependency pin allows 3.9+, so a fresh environment fails every analysis at the last step.

**Decision.** Turn the findings into a sequenced roadmap of independently-reviewable PRs
([`ROADMAP.md`](./ROADMAP.md)), front-loading the work that reinforces the platform/infra/MLOps
narrative: foundation & CI → critical fixes → security → reproducibility → durability → performance.
Auth is highest-leverage, because without it every other safeguard is moot.

**Capability demonstrated.** Systematic, evidence-driven assessment of an unfamiliar multi-service
system; the ability to separate "already done well" from "actually risky" and to sequence the fixes
by leverage rather than by ease.

---

## Sprint 1 — Foundation, CI, and critical fixes *(in progress)*

The opening sprint clears the ground so every later change lands on a regression-gated, reproducible
base. It maps to roadmap epics **A** and **B**.

- **PR 1 — Repo hygiene & docs.** Make the repo tell the truth: fix the doc/script references that
  point at files that no longer exist, remove the half-declared Gradle modules that can't build,
  prune dead duplicates, and pin the Python dependencies (including the Matplotlib ceiling).
- **PR 2 — Wire CI.** Turn the empty workflow placeholders into real gates: compile + test the
  backend, type-check + test the frontend, run the region-detector tests, and scan dependencies.
  Nothing else in the roadmap is safe to merge until a regression can fail a check.
- **PR 3 — Matplotlib fix.** A one-line API migration that restores the heatmap stage on any clean
  install — small change, but it's the difference between "works on the original dev box" and
  "works anywhere."
- **PR 4 — Tile-serving crash + global error handling.** Stop a malformed tile request from
  returning a 500, and route all controller errors through one handler so internals stop leaking.

### Results

Branches (each opens as its own PR; non-overlapping files):

| Branch | Roadmap PR | What landed |
|--------|-----------|-------------|
| `chore/repo-hygiene-and-docs` | 1 | doc/script/gradle/deps cleanup |
| `ci/wire-github-actions` | 2 | five GitHub Actions workflows |
| `chore/refresh-frontend-tests` | 2 (companion) | un-rot the frontend suite |
| `fix/heatmap-matplotlib-colormap` | 3 | Matplotlib ≥3.9 fix |
| `fix/tile-coord-parse-and-error-handling` | 4 | tile-serving robustness |

**PR 1 — Repo hygiene.** Pointed the README at the real entry point (`./dev.sh`), corrected the
`docker/README` helper-script paths, and fixed a `REPO_ROOT` bug shared by all three
`scripts/docker/*.sh` helpers — they resolved one directory *above* the repo root, so the e2e
harness could never have run as written. Dropped the `apps:api`/`libs:common` Gradle modules that
were declared in `settings.gradle.kts` but had no build files (plus the stray, divergent
`backend/apps` source copy), leaving a single honest module rooted at `backend/src`. Pinned the
previously-unpinned tiling dependencies.
*Why it matters:* a repo that lies about how to run it is a repo nobody can onboard to. This is the
unglamorous groundwork that makes the reproducibility story credible.

**PR 2 — Wire CI + un-rot the tests.** The five workflow files existed but were empty, so no test
had ever run in CI. Wired backend (`./gradlew test`), frontend (`tsc` + `vitest`), region-detector
(`pytest` on Python 3.11 with a torch-free `requirements-test.txt`), a shellcheck lint lane, a
compose-based integration smoke test, and a Trivy security scan — with deliberate severity tiers:
unit/type checks block, while the heavy compose job and the secret-finding scan start report-only
and tighten as later PRs remove the committed credentials.

The moment I ran the suites locally, they came back **red — and not because of anything I changed.**
*Two independent test-rot finds, in two different languages:*

- **Frontend:** two tests asserted UI that no longer exists — a nav link renamed `Tile Viewer` →
  `Viewer`, and a "Backend activity" history panel + combined `message. 42%` string that a later UI
  simplification had removed. Refreshed the assertions to the UI's real current contract (6/6 green).
- **region-detector:** `test_variance_fallback_catches_non_he_content` built a tile with three
  *independent* random RGB channels — which is colourful (high HSV saturation), so it passed the
  primary saturation check and contradicted its own assertion that the variance fallback was needed.
  The code was correct; the test didn't match its own docstring ("grayscale photo"). Fixed it to use
  true grayscale (R=G=B), matching its sibling test (28/28 green).

This is the entire argument for CI in one observation: *both suites had been silently broken and
nobody knew, because nothing ran them.* I fixed the tests to their real contracts so the gates are
genuinely green rather than green-because-weakened.
*Why it matters:* CI is the ratchet. Every later epic — security, durability, model versioning —
relies on a regression being *catchable*. This turns that on.

**PR 3 — Matplotlib fix.** `matplotlib.cm.get_cmap` was removed in 3.9; with the dependency floor at
`>=3.7` a clean install resolved 3.9+ and the heatmap stage crashed on every analysis. Migrated to
the `matplotlib.colormaps[...]` registry (stable since 3.5).
*Why it matters:* "works on the original dev box" is not reproducible. A one-line change is the
difference between a demo and something a teammate can actually run.

**PR 4 — Tile-serving robustness.** `getTile` parsed the `{coord}` path variable with a destructuring
`toInt()` *before* its try block, so any malformed coordinate (no underscore, non-numeric) threw an
uncaught exception → HTTP 500 on the single hottest endpoint in the system (a Deep Zoom view fires
hundreds of tile requests). Now parses defensively and returns 404. Also routed four bare
`printStackTrace()` calls through SLF4J. The broader global `@RestControllerAdvice` is deferred to a
dedicated PR so it can land with the `@WebMvcTest` updates it requires.
*Why it matters:* the serving layer should degrade gracefully under bad input, not stack-trace.

### What I couldn't verify here, and how it's covered

No JDK is available in this working environment, so the Kotlin changes (PR 4) and the backend test
gate (PR 2) were not compiled locally — they are exercised by `backend-ci` on push. Everything else
*was* run locally: the frontend suite (`tsc --noEmit` clean; `vitest` 6/6 green) and the
region-detector suite (`pytest` on Python 3.11 — the CI version — 28/28 green). Calling this out
explicitly rather than implying everything was verified end-to-end.

---

## Sprint 2 — Security foundation

The highest-leverage work: until the API is authenticated, every other safeguard is moot. Four PRs,
all verified locally.

| Branch | Roadmap PR | What landed |
|--------|-----------|-------------|
| `security/externalize-secrets` | 5 | env-placeholder credentials, no committed secrets in app config |
| `security/internal-callback-auth` | 6 | shared-secret guard on the internal callbacks |
| `security/api-auth-jwt` | 8 | self-issued HS256 JWT auth on the public API |
| `security/tenant-scoped-sse` | 7 | per-tenant SSE delivery (depends on 8) |

**Mid-sprint unlock: a JDK appeared.** Sprint 1 shipped the Kotlin changes unverified-locally (no
Java runtime), leaning on CI. Partway through Sprint 2 I found Homebrew's OpenJDK 17 installed but
unlinked — the backend's exact toolchain. From that point every backend PR was **compiled and tested
locally** before pushing. That mattered immediately (see PR 8).

**PR 5 — Externalize secrets.** The deployment-shaped `application.yml` carried live DB and MinIO
credentials. Replaced the actual secrets (`POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`) with *required*
env placeholders — no default, so the app fails fast if unset — while non-secret fields keep
local-dev defaults. Compose creds became overridable interpolation so the dev stack still runs
zero-config. Verified the test profile shadows the file (backend suite stayed green).
*Why it matters:* the config a deployment consumes no longer leaks a secret, and prod is forced to
inject real ones.

**PR 6 — Authenticate the internal callbacks.** The `/api/v1/internal/` endpoints — which let a
caller set job state and repoint result artifacts — were wide open. Added a constant-time
`X-Internal-Token` filter scoped to that URL space, fail-closed when unconfigured, with the tiling
and region-detector services sending the token. A KDoc containing a wildcard internal path initially
broke the build: Kotlin supports *nested* block comments, so the `/` `*` inside the path opened a
comment the KDoc's close then consumed — leaving the real comment unterminated. Caught and fixed
locally.
*Why it matters:* the result a clinician would read can no longer be forged by an unauthenticated
caller.

**PR 8 — JWT auth on the public API.** Added self-issued HS256 JWT auth (no external IdP): a stateless
resource-server filter chain, a `/api/v1/auth/login` that mints a token with a subject and tenant
claim, and overridable dev credentials. The local test caught a real bug immediately:
`NimbusJwtEncoder` defaults to RS256 and couldn't sign with the symmetric key
("Failed to select a JWK signing key") — fixed by setting the HS256 JWS header explicitly. This is
the payoff of the JDK find: that bug would otherwise have shipped to CI. Existing controller slice
tests were kept green by disabling their security filters (`addFilters = false`); the token logic and
full-context wiring are covered by dedicated tests.
*Why it matters:* the platform now has an identity boundary — the prerequisite for tenancy, audit,
and per-user scoping.

**PR 7 — Per-tenant SSE.** With the stream now authenticated, partitioned event delivery by tenant:
a tenant column on the tiling job (captured from the JWT at creation), a `TenantContext` that reads
the tenant claim, and a tenant-keyed emitter registry that publishes a job event only to its tenant's
subscribers. `registerEmitter()` kept its signature (reads the tenant internally), so the controller
and its test were untouched.
*Why it matters:* one tenant's job activity is no longer broadcast to every connected client.

### Honest scope boundaries

- **PR 7 is the *minimum* tenant tagging the SSE objective needs** — not the full domain model. The
  `Patient`/`Case`/`Slide` entities, Flyway migrations to backfill the tenant column, and
  tenant-scoping of the REST read paths remain as data-model roadmap items.
- **Auth is enforced on `/api/v1/`, so the running app needs a frontend token integration**
  (login + `Authorization` header, OSD `ajaxHeaders` for tiles) before it works end-to-end. That
  frontend companion is the required next step. CI is unaffected (integration smoke hits only
  permitAll health; frontend tests mock fetch).

*Sprint 2 demonstrates the security and identity layer of the platform: secret hygiene, service-to-
service auth, user auth, and the first slice of tenancy — each as an independently-reviewable,
locally-verified PR.*

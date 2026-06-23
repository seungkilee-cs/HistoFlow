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

*(Per-PR completion notes are appended below as each lands.)*

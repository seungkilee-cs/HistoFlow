# ADR-007: Shared ML Base Images and Compose Profiles for ML Services

Date: 2026-01-01
Status: Proposed

## Context

We are adding the `sk-regression` service to our containerized stack and expect to add more ML services later. Our current docker composition includes `backend`, `minio`, `postgres`, and a lightweight Python `tiling` service. ML services often depend on heavier frameworks (PyTorch, scikit-learn, potentially TensorFlow), optional GPU runtimes, and large native libraries. If we bake all ML dependencies into a single shared container or into unrelated services, we risk:

- Significant image bloat for services that do not need ML frameworks.
- Conflicting dependency requirements between ML stacks (CUDA versions, torch/TF versions).
- Poor resource isolation (GPU reservations, CPU contention) and slower builds.

We need an approach that:
- Integrates `sk-regression` seamlessly into the existing stack.
- Keeps each ML service as lean as possible while enabling reuse of common layers.
- Allows CPU and GPU variants without duplicating service logic.
- Scales to additional ML services with minimal friction.

## Decision

Adopt a two-tier strategy:

1) ML Base Images (CPU and CUDA)
- Build and version two reusable base images:
  - `ml-base-cpu`: Based on `pytorch/pytorch:<torch-version>-cpu`. Includes shared ML runtime deps common across our services (torch/torchvision CPU, numpy, pillow, opencv-python-headless, joblib, minio SDK, and optionally FastAPI/uvicorn for services exposing HTTP).
  - `ml-base-cuda`: Based on `pytorch/pytorch:<torch-version>-cudaXX-runtime`. Mirrors the CPU image but with CUDA-enabled torch, appropriate for GPU nodes.
- Each ML service (e.g., `sk-regression`) extends one of these bases and installs only its service-specific extras in a thin layer.

2) Docker Compose Profiles for CPU/GPU/Dev
- Define `cpu`, `gpu`, and `dev` profiles. ML services can declare GPU resource reservations under the `gpu` profile and run without GPUs under the `cpu` profile.
- Keep ML services in a separate compose file (e.g., `docker/docker-compose.ml.yml`) that can be layered with the base stack.

## Alternatives Considered

1) Single Monolithic ML Image for All Services
- Pros: One image to maintain; simplicity.
- Cons: Large image with many unnecessary deps per service; conflicting versions; less cache reuse across divergent stacks; harder GPU/CPU splitting.
- Rejected due to bloat and poor isolation.

2) Reusing the Existing `tiling` Python Image
- Pros: Fewer images.
- Cons: Tiling is intentionally lightweight; adding torch/TF would bloat it, break its minimal footprint, and complicate runtime (CUDA). Also risks dependency conflicts and slows CI.
- Rejected to preserve tiling simplicity and separation of concerns.

3) Service-Local, Fully Independent Images (No Shared Base)
- Pros: Maximum isolation; each service controls its full stack.
- Cons: Duplicate dependency installation; slower builds; harder to standardize logging/observability; missed caching opportunities.
- Rejected because a shared base strikes a better balance between reuse and isolation.

4) Conda/Mamba Multi-Env in a Single Container
- Pros: Can host multiple envs in one container; flexible locally.
- Cons: Operational complexity in containers; larger image; startup/runtime env activation complexity; not aligned with our existing service-per-container pattern.
- Rejected for operational simplicity and consistency.

## Consequences

- Faster builds for new ML services by leveraging cached `ml-base-*` layers.
- Clear separation between CPU and GPU runtimes using compose profiles.
- Each ML service remains minimal, installing only unique dependencies.
- Backend and tiling containers remain small and unaffected by ML framework bloat.
- We can incrementally roll out changes to base images and pin services to compatible tags.

## Rollout Plan

- Add `sk-regression` as a service to the master docker stack, using the CPU profile by default.
- Introduce `docker/docker-compose.ml.yml` that contains ML services (starting with `sk-regression`).
- Define profiles: `cpu`, `gpu`, `dev`.
- Publish `ml-base-cpu` and `ml-base-cuda` images in the build registry; version with torch/python/cuda matrix (e.g., `ml-base-cpu:2.4-py3.10`, `ml-base-cuda:2.4-cuda12.4`).
- Update ML service Dockerfiles to accept `ARG BASE_IMAGE` and default to `ml-base-cpu`.
- For GPU deployments, override the base via `--build-arg BASE_IMAGE=<ml-base-cuda-tag>` and enable the `gpu` profile.

## Open Questions

- Do we want to expose ML services via REST (FastAPI) or keep CLI-only with `docker exec`? Decision impacts which libraries belong in the base.
- How should we manage model artifacts (central model registry vs per-service volume)? For now, continue using per-service volumes mounted into `/app/models`.
- How aggressively do we pin dependency versions in the base to maximize cache reuse while avoiding incompatibility?

## References

- Existing stack: `docker/docker-compose.base.yml`, `docker/docker-compose.dev.yml`.
- ML Example: `services/sk-regression/Dockerfile`, `services/tiling/Dockerfile`.

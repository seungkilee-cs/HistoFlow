# ADR-005: Persisting Tiling Job Status in the Backend

Status: Draft  
Date: 2025-11-06

## Context
- Multipart uploads trigger the tiling microservice, but the backend currently has no source of truth for job status.
- Frontend polling needs a reliable endpoint that reflects whether tiling is pending, running, completed, or failed.
- Previous ADR-004 established synchronous triggering plus polling; this ADR adds durable tracking to support that flow.

## Decision
Introduce a persistent `tiling_jobs` table managed by Spring Data JPA:
- Define a `TilingJobStatus` enum to codify lifecycle states (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`).
- Create a `TilingJobEntity` with fields for dataset metadata, failure reason, timestamps, and optional metadata path.
- Register a `TilingJobRepository` for querying by `imageId`.
- Return API-friendly DTOs that expose status, timestamps, and failure context for frontend polling.

This preserves the existing layered package structure by nesting new types under `domain/tiling`, `dto/tiling`, and `repository/tiling` while controllers/services remain in their respective packages.

## Alternatives Considered
1. In memory map (no persistence): rejected because deployments are stateless and would lose status on restart.
2. Storing status in MinIO metadata: rejected; puts application state in object storage and complicates querying.
3. Message-queue-driven status service: deferred; adds infrastructure overhead before it’s needed.

## Consequences
- Backend gains a consistent model for tiling jobs, unlocking reliable polling APIs and future analytics.
- Requires a database migration (`tiling_jobs` table) and ongoing lifecycle management (e.g., cleanup policies).
- Establishes a pattern for future domains (classification, analysis) to add their own subpackages under `domain/` and related layers without fragmenting the project layout.

## Related ADRs
- ADR-001: Tiling Microservice
- ADR-003: File Upload Pattern
- ADR-004: Upload Completion Notification and Automatic Tiling Trigger

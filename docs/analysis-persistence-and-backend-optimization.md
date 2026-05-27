# Analysis Persistence and Backend Optimization Plan

Date: 2026-05-27  
Scope: backend architecture for saving, loading, rerunning, and comparing cancer analysis results over time by slide and patient.

## Goal

HistoFlow should persist every cancer analysis run as a reproducible historical record, allow users to save/pin clinically meaningful results, reload result artifacts, rerun models over time, and compare outcomes for a slide or patient timeline.

## Current context

The current backend already has an analysis seed model through `AnalysisJobEntity`, `AnalysisService`, `AnalysisJobRepository`, and `AnalysisController`:

- analysis jobs have `jobId`, `imageId`, status, tile/model parameters, progress counters, artifact keys, summary metrics, error message, and timestamps;
- the region-detector writes summary, heatmap, and tile prediction artifacts to object storage;
- the backend loads summaries/results by object key and exposes status, results, heatmap, and image-specific history endpoints.

This is a good starting point, but it should evolve from a single job-centric model into a durable analysis history model connected to slides, patients, model versions, and saved user interpretations.

## Recommended backend structure

Treat analysis results as immutable run history. Do not overwrite a previous completed analysis when a model is rerun.

### Core entities

#### Patient

Represents the person or pseudonymous subject associated with one or more cases/slides.

Suggested fields:

- `id`
- `externalPatientId` or pseudonymous identifier
- optional demographics/metadata, if required
- tenant or organization ownership
- timestamps

#### Case / Study / Encounter

Groups one or more slides under a clinical event, upload batch, visit, or study.

Suggested fields:

- `id`
- `patientId`
- name/title
- diagnosis/context metadata
- created/uploaded timestamp

#### Slide

Represents a source image and its tiling metadata.

Suggested fields:

- `id`
- `patientId`
- `caseId`
- `imageId`
- source bucket/object key
- DZI descriptor key
- tile metadata/manifest key
- dimensions
- available tile levels
- stain/scanner metadata, if known
- timestamps

#### AnalysisRun

Represents one execution of one model with one parameter set against one slide.

Suggested fields:

- `id`
- `jobId`
- `slideId`
- denormalized `patientId` for fast timeline queries
- `status`: queued, running, completed, failed, cancelled
- `modelVersionId`
- parameters: tile level, threshold, tissue threshold, batch size
- progress: tiles processed, total tiles
- summary metrics: tumor area percentage, aggregate score, max score, tissue tile count, skipped tile count
- artifact references: summary key, heatmap key, tile prediction key, report key
- started/completed timestamps
- error message if failed

#### ModelVersion

Captures reproducibility metadata for model outputs.

Suggested fields:

- `id`
- model name
- semantic version or run version
- artifact checksum/hash
- backbone
- classifier/head version
- preprocessing version
- calibration version
- training dataset/version
- active/deprecated flag
- created timestamp

#### AnalysisArtifact

Indexes stored result artifacts without loading the heavy payload into Postgres.

Suggested fields:

- `id`
- `analysisRunId`
- type: summary, heatmap, tile_predictions, overlay, report
- bucket/object key
- content type
- size
- checksum
- created timestamp

#### SavedAnalysis / Interpretation

Represents the user-facing saved/pinned result. This should reference an immutable `AnalysisRun`; it should not be the source of truth for model output.

Suggested fields:

- `id`
- `analysisRunId`
- `slideId`
- `patientId`
- title/name
- notes
- savedBy/reviewedBy
- pinned/favorite/report-ready flag
- clinical review state, if needed
- timestamps

## Save semantics

Persist every analysis run automatically. A completed run should already be durable before a user clicks save.

The user action "save" should mean one or more of:

- pin this run to the slide or patient timeline;
- add user notes or interpretation;
- mark as reviewed/approved;
- include in a report;
- preserve as the preferred result among many runs.

This preserves reproducibility and avoids accidental loss of model outputs.

## API shape

### Trigger or rerun analysis

```http
POST /api/v1/slides/{slideId}/analyses
```

Example request:

```json
{
  "modelVersionId": "dinov2-pcam-v1",
  "tileLevel": 12,
  "threshold": 0.5,
  "tissueThreshold": 0.15,
  "reason": "follow-up comparison"
}
```

This creates a new immutable `AnalysisRun`.

### List analysis history

```http
GET /api/v1/slides/{slideId}/analyses
GET /api/v1/patients/{patientId}/analyses
```

Return paginated summaries only. Do not inline full tile predictions.

### Load one analysis run

```http
GET /api/v1/analyses/{analysisRunId}
```

Return status, model version, parameters, summary metrics, artifact references, and timestamps.

### Load heavy artifacts separately

```http
GET /api/v1/analyses/{analysisRunId}/heatmap
GET /api/v1/analyses/{analysisRunId}/tile-predictions?bbox=...
GET /api/v1/analyses/{analysisRunId}/artifacts
```

For very large tile prediction outputs, prefer pagination, bounding-box queries, chunked artifacts, or presigned object-storage URLs.

### Save, pin, or annotate a result

```http
POST /api/v1/analyses/{analysisRunId}/saved
PATCH /api/v1/saved-analyses/{savedAnalysisId}
```

## Processing flow

1. User uploads a slide.
2. Backend creates a `Slide` record.
3. Tiling job creates DZI tiles and metadata.
4. User triggers analysis.
5. Backend creates `AnalysisRun(status=QUEUED)`.
6. Worker runs model.
7. Worker writes artifacts to MinIO/S3:
   - summary JSON;
   - heatmap PNG;
   - tile predictions JSON/NDJSON/Parquet;
   - optional report/overlay artifacts.
8. Worker sends completion/failure event.
9. Backend updates `AnalysisRun` with status, summary fields, artifact references, model version, runtime metrics, and error state if applicable.
10. User can load, compare, save, annotate, or rerun.

## Existing-code optimization candidates

Ordered roughly by benefit-to-risk ratio for incremental changes.

1. Add database ordering and pagination for analysis history.
2. Store/load analysis summaries from Postgres while keeping heavy artifacts in object storage.
3. Add indexes for analysis history and job lookup queries.
4. Avoid returning full tile predictions by default; expose paginated/artifact endpoints.
5. Persist dataset/tile metadata in Postgres to avoid repeated MinIO scans for dataset listings.
6. Consolidate single-upload and multipart-upload behavior so all tiling paths create a persisted job.
7. Move bucket creation checks from per-request upload paths to startup/initialization.
8. Make tiling upload worker count and analysis concurrency settings configurable.
9. Add model-version records and attach each analysis run to an explicit model/preprocessing version.
10. Add queue-based job dispatch with bounded workers and retry/dead-letter handling.
11. Add analysis comparison/timeline endpoints for slide and patient history.
12. Cache reusable analysis intermediates such as tile manifests, tissue masks, and embeddings.

## Performance principles

- Keep queryable summaries in Postgres.
- Keep heavy artifacts in object storage.
- Never overwrite completed runs.
- Version every model and preprocessing path.
- Make history endpoints summary-first and paginated.
- Make expensive worker execution asynchronous and bounded.
- Optimize object-storage scans out of user-facing request paths.
- Cache only artifacts whose cache key includes slide, tile level, model version, preprocessing version, and relevant thresholds.

## Medical/privacy notes

If patient-linked data is stored, design for protected health information from the beginning: access control, audit logs, encryption, least-privilege storage access, retention/deletion policies, and environment separation.

References:

- HHS HIPAA Security Rule: https://www.hhs.gov/hipaa/for-professionals/security/index.html
- FDA Software as a Medical Device overview: https://www.fda.gov/MedicalDevices/DigitalHealth/SoftwareasaMedicalDevice/default.htm
- FDA Clinical Decision Support Software FAQ: https://www.fda.gov/medical-devices/software-medical-device-samd/clinical-decision-support-software-frequently-asked-questions-faqs

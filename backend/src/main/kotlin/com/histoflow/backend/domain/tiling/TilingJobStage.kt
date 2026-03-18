package com.histoflow.backend.domain.tiling

/**
 * High-level execution stage for a tiling job.
 */
enum class TilingJobStage {
    QUEUED,
    DOWNLOADING,
    TILING,
    UPLOADING,
    COMPLETED,
    FAILED
}

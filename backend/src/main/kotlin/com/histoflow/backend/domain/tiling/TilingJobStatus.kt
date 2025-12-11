package com.histoflow.backend.domain.tiling

/**
 * Represents the lifecycle states for a tiling job triggered after an upload completes.
 */
enum class TilingJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

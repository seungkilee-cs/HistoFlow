package com.histoflow.backend.dto.tiling

import com.histoflow.backend.domain.tiling.TilingJobStatus
import java.time.Instant

/**
 * API-facing representation of a tiling job's status for polling endpoints.
 */
data class TilingJobStatusResponse(
    val imageId: String,
    val datasetName: String?,
    val status: TilingJobStatus,
    val failureReason: String? = null,
    val metadataPath: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

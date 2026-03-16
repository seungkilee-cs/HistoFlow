package com.histoflow.backend.dto.tiling

import com.histoflow.backend.domain.tiling.TilingJobStatus
import com.histoflow.backend.domain.tiling.TilingJobStage
import java.time.Instant
import java.util.UUID

/**
 * API-facing representation of a tiling job's status for polling endpoints.
 */
data class TilingJobStatusResponse(
    val id: UUID,
    val imageId: String,
    val datasetName: String?,
    val status: TilingJobStatus,
    val stage: TilingJobStage,
    val message: String? = null,
    val failureReason: String? = null,
    val metadataPath: String? = null,
    val stageProgressPercent: Int? = null,
    val activityEntries: List<TilingJobActivityEntry> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)

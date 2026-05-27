package com.histoflow.backend.dto.analysis

import com.histoflow.backend.domain.analysis.AnalysisJobStatus
import java.time.Instant
import java.util.UUID

data class SaveAnalysisRequest(
    val title: String? = null,
    val notes: String? = null,
    val pinned: Boolean? = null
)

data class SavedAnalysisResponse(
    val id: UUID,
    val jobId: String,
    val imageId: String,
    val title: String?,
    val notes: String?,
    val pinned: Boolean,
    val status: AnalysisJobStatus,
    val tileLevel: Int?,
    val threshold: Float?,
    val tissueThreshold: Float?,
    val tumorAreaPercentage: Double?,
    val aggregateScore: Double?,
    val maxScore: Double?,
    val heatmapKey: String?,
    val summaryKey: String?,
    val resultsKey: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

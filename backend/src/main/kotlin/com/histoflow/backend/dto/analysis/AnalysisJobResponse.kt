package com.histoflow.backend.dto.analysis

import com.histoflow.backend.domain.analysis.AnalysisJobStatus
import java.util.UUID

data class AnalysisJobResponse(
    val id: UUID,
    val jobId: String,
    val imageId: String,
    val status: AnalysisJobStatus,
    val tileLevel: Int?,
    val threshold: Float?,
    val tissueThreshold: Float?,
    val tilesProcessed: Int,
    val totalTiles: Int,
    val tumorAreaPercentage: Double?,
    val aggregateScore: Double?,
    val maxScore: Double?,
    val heatmapKey: String?,
    val modelName: String?,
    val errorMessage: String?
)

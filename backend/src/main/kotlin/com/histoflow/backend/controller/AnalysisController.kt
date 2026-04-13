package com.histoflow.backend.controller

import com.histoflow.backend.dto.analysis.AnalysisJobResponse
import com.histoflow.backend.service.AnalysisService
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/analysis")
class AnalysisController(
    private val analysisService: AnalysisService
) {
    private val logger = LoggerFactory.getLogger(AnalysisController::class.java)

    @PostMapping("/trigger/{imageId}")
    fun triggerAnalysis(
        @PathVariable imageId: String,
        @RequestParam(required = false) tileLevel: Int?,
        @RequestParam(required = false) threshold: Float?,
        @RequestParam(required = false) tissueThreshold: Float?,
        @RequestParam(required = false) batchSize: Int?
    ): ResponseEntity<*> {
        return try {
            val response = analysisService.triggerAnalysis(
                imageId = imageId,
                tileLevel = tileLevel,
                threshold = threshold,
                tissueThreshold = tissueThreshold,
                batchSize = batchSize
            )
            ResponseEntity.ok(response)
        } catch (e: AnalysisService.AnalysisProxyException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/status/{jobId}")
    fun getStatus(@PathVariable jobId: String): ResponseEntity<*> {
        return try {
            val status = analysisService.getStatus(jobId)
            ResponseEntity.ok(status)
        } catch (e: AnalysisService.AnalysisProxyException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/results/{jobId}")
    fun getResults(@PathVariable jobId: String): ResponseEntity<*> {
        return try {
            val results = analysisService.getResults(jobId)
            ResponseEntity.ok(results)
        } catch (e: AnalysisService.AnalysisProxyException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/heatmap/{jobId}")
    fun getHeatmap(@PathVariable jobId: String): ResponseEntity<Any> {
        return try {
            val body = InputStreamResource(analysisService.getHeatmapObjectForJob(jobId))

            ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body)
        } catch (e: AnalysisService.AnalysisProxyException) {
            val status = if (e.statusCode == 202) HttpStatus.CONFLICT else HttpStatus.valueOf(e.statusCode)
            if (status.is5xxServerError) {
                logger.error("Heatmap retrieval failed for job {}: {}", jobId, e.message)
            }
            ResponseEntity.status(status).body(mapOf("error" to e.message))
        }
    }

    data class AnalysisHistoryResponse(val jobs: List<AnalysisJobResponse>)

    @GetMapping("/history/{imageId}")
    fun getHistory(@PathVariable imageId: String): ResponseEntity<AnalysisHistoryResponse> =
        ResponseEntity.ok(AnalysisHistoryResponse(analysisService.getHistoryForImage(imageId)))
}

data class AnalysisJobEventRequest(
    val status: com.histoflow.backend.domain.analysis.AnalysisJobStatus,
    @com.fasterxml.jackson.annotation.JsonProperty("image_id")
    val imageId: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("tile_level")
    val tileLevel: Int? = null,
    val threshold: Float? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("tissue_threshold")
    val tissueThreshold: Float? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("tiles_processed")
    val tilesProcessed: Int? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("total_tiles")
    val totalTiles: Int? = null,
    val message: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("heatmap_key")
    val heatmapKey: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("summary_key")
    val summaryKey: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("results_key")
    val resultsKey: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("tumor_area_percentage")
    val tumorAreaPercentage: Double? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("aggregate_score")
    val aggregateScore: Double? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("max_score")
    val maxScore: Double? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("error_message")
    val errorMessage: String? = null
)

@RestController
@RequestMapping("/api/v1/internal/analysis")
class InternalAnalysisJobController(
    private val analysisService: AnalysisService
) {
    @PostMapping("/jobs/{jobId}/events")
    fun updateJob(
        @PathVariable jobId: String,
        @RequestBody request: AnalysisJobEventRequest
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(
                analysisService.updateJob(
                    jobId = jobId,
                    update = AnalysisService.AnalysisJobEventUpdate(
                        status = request.status,
                        imageId = request.imageId,
                        tileLevel = request.tileLevel,
                        threshold = request.threshold,
                        tissueThreshold = request.tissueThreshold,
                        tilesProcessed = request.tilesProcessed,
                        totalTiles = request.totalTiles,
                        message = request.message,
                        heatmapKey = request.heatmapKey,
                        summaryKey = request.summaryKey,
                        resultsKey = request.resultsKey,
                        tumorAreaPercentage = request.tumorAreaPercentage,
                        aggregateScore = request.aggregateScore,
                        maxScore = request.maxScore,
                        errorMessage = request.errorMessage
                    )
                )
            )
        } catch (e: AnalysisService.AnalysisProxyException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }
}

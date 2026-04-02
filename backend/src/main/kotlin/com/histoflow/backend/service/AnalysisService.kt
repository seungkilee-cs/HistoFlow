package com.histoflow.backend.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.histoflow.backend.config.AnalysisProperties
import com.histoflow.backend.config.MinioProperties
import com.histoflow.backend.domain.analysis.AnalysisJobEntity
import com.histoflow.backend.domain.analysis.AnalysisJobStatus
import com.histoflow.backend.repository.analysis.AnalysisJobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.io.InputStream

@Service
class AnalysisService(
    private val restTemplate: RestTemplate,
    private val analysisProperties: AnalysisProperties,
    private val s3Client: S3Client,
    private val minioProperties: MinioProperties,
    private val analysisJobRepository: AnalysisJobRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(AnalysisService::class.java)
    private val tilePredictionListType = object : TypeReference<List<TilePredictionResponse>>() {}

    class AnalysisProxyException(
        val statusCode: Int,
        override val message: String
    ) : RuntimeException(message)

    data class AnalyzeRequest(
        val job_id: String,
        val image_id: String,
        val tile_level: Int? = null,
        val threshold: Float? = null,
        val tissue_threshold: Float? = null,
        val batch_size: Int = 16
    )

    data class AnalyzeResponse(
        val job_id: String,
        val status: String,
        val message: String
    )

    data class AnalysisStatusResponse(
        val status: String,
        @JsonProperty("image_id")
        val imageId: String? = null,
        @JsonProperty("tile_level")
        val tileLevel: Int? = null,
        @JsonProperty("tiles_processed")
        val tilesProcessed: Int = 0,
        @JsonProperty("total_tiles")
        val totalTiles: Int = 0,
        val message: String? = null
    )

    data class DziResponse(
        val width: Int,
        val height: Int,
        @JsonProperty("tile_size")
        val tileSize: Int,
        val format: String
    )

    data class AnalysisSummaryResponse(
        @JsonProperty("total_tiles")
        val totalTiles: Int,
        @JsonProperty("tissue_tiles")
        val tissueTiles: Int,
        @JsonProperty("skipped_tiles")
        val skippedTiles: Int,
        @JsonProperty("flagged_tiles")
        val flaggedTiles: Int,
        @JsonProperty("tumor_area_percentage")
        val tumorAreaPercentage: Double,
        @JsonProperty("aggregate_score")
        val aggregateScore: Double,
        @JsonProperty("max_score")
        val maxScore: Double,
        @JsonProperty("aggregation_method")
        val aggregationMethod: String,
        val threshold: Double
    )

    data class TilePredictionResponse(
        @JsonProperty("tile_x")
        val tileX: Int,
        @JsonProperty("tile_y")
        val tileY: Int,
        @JsonProperty("tile_level")
        val tileLevel: Int,
        @JsonProperty("pixel_x")
        val pixelX: Int,
        @JsonProperty("pixel_y")
        val pixelY: Int,
        val width: Int,
        val height: Int,
        @JsonProperty("is_tissue")
        val isTissue: Boolean,
        @JsonProperty("tissue_ratio")
        val tissueRatio: Double,
        @JsonProperty("tumor_probability")
        val tumorProbability: Double,
        val label: String
    )

    data class AnalysisResultResponse(
        @JsonProperty("image_id")
        val imageId: String,
        @JsonProperty("tile_level")
        val tileLevel: Int,
        val dzi: DziResponse? = null,
        val summary: AnalysisSummaryResponse? = null,
        @JsonProperty("heatmap_key")
        val heatmapKey: String? = null,
        @JsonProperty("summary_key")
        val summaryKey: String? = null,
        @JsonProperty("results_key")
        val resultsKey: String? = null,
        val timings: Map<String, Double>? = null,
        @JsonProperty("tile_predictions")
        val tilePredictions: List<TilePredictionResponse> = emptyList()
    )

    data class AnalysisJobEventUpdate(
        val status: AnalysisJobStatus,
        @JsonProperty("image_id")
        val imageId: String? = null,
        @JsonProperty("tile_level")
        val tileLevel: Int? = null,
        val threshold: Float? = null,
        @JsonProperty("tissue_threshold")
        val tissueThreshold: Float? = null,
        @JsonProperty("tiles_processed")
        val tilesProcessed: Int? = null,
        @JsonProperty("total_tiles")
        val totalTiles: Int? = null,
        val message: String? = null,
        @JsonProperty("heatmap_key")
        val heatmapKey: String? = null,
        @JsonProperty("summary_key")
        val summaryKey: String? = null,
        @JsonProperty("results_key")
        val resultsKey: String? = null,
        @JsonProperty("tumor_area_percentage")
        val tumorAreaPercentage: Double? = null,
        @JsonProperty("aggregate_score")
        val aggregateScore: Double? = null,
        @JsonProperty("max_score")
        val maxScore: Double? = null,
        @JsonProperty("error_message")
        val errorMessage: String? = null
    )

    private data class StoredAnalysisSummary(
        @JsonProperty("image_id")
        val imageId: String,
        @JsonProperty("tile_level")
        val tileLevel: Int,
        val dzi: DziResponse? = null,
        val summary: AnalysisSummaryResponse? = null,
        @JsonProperty("heatmap_key")
        val heatmapKey: String? = null,
        @JsonProperty("tile_predictions_key")
        val tilePredictionsKey: String? = null,
        val timings: Map<String, Double>? = null
    )

    fun triggerAnalysis(
        imageId: String,
        tileLevel: Int? = null,
        threshold: Float? = null,
        tissueThreshold: Float? = null,
        batchSize: Int? = null
    ): AnalyzeResponse {
        val jobId = java.util.UUID.randomUUID().toString()
        val url = "${analysisProperties.baseUrl}/jobs/analyze"
        val request = AnalyzeRequest(
            job_id = jobId,
            image_id = imageId,
            tile_level = tileLevel,
            threshold = threshold,
            tissue_threshold = tissueThreshold,
            batch_size = batchSize ?: 16
        )
        val entity = AnalysisJobEntity(
            jobId = jobId,
            imageId = imageId,
            status = AnalysisJobStatus.PROCESSING,
            tileLevel = tileLevel,
            threshold = threshold,
            tissueThreshold = tissueThreshold,
            statusMessage = "Analysis job accepted and started."
        )
        analysisJobRepository.save(entity)

        logger.info("Triggering analysis for image {} with request {} via {}", imageId, request, url)

        try {
            val response = restTemplate.postForObject(url, request, AnalyzeResponse::class.java)
            if (response == null) {
                markJobFailed(jobId, "Empty response from analysis service")
                throw AnalysisProxyException(502, "Empty response from analysis service")
            }
            val persisted = analysisJobRepository.findByJobId(jobId).orElse(entity)
            persisted.statusMessage = response.message.ifBlank { persisted.statusMessage }
            analysisJobRepository.save(persisted)
            return response.copy(job_id = jobId)
        } catch (e: HttpStatusCodeException) {
            markJobFailed(jobId, e.responseBodyAsString.ifBlank { e.message ?: "Analysis trigger failed" })
            throw toProxyException("trigger", e)
        } catch (e: AnalysisProxyException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to trigger analysis for image {}: {}", imageId, e.message)
            markJobFailed(jobId, "Failed to reach analysis service: ${e.message}")
            throw AnalysisProxyException(502, "Failed to reach analysis service: ${e.message}")
        }
    }

    fun getStatus(jobId: String): AnalysisStatusResponse {
        val entity = findJobOrThrow(jobId)
        return AnalysisStatusResponse(
            status = entity.status.asApiValue(),
            imageId = entity.imageId,
            tileLevel = entity.tileLevel,
            tilesProcessed = entity.tilesProcessed,
            totalTiles = entity.totalTiles,
            message = entity.statusMessage ?: defaultMessageForStatus(entity.status)
        )
    }

    fun getResults(jobId: String): AnalysisResultResponse {
        val entity = findJobOrThrow(jobId)

        when (entity.status) {
            AnalysisJobStatus.PROCESSING -> {
                throw AnalysisProxyException(202, entity.statusMessage ?: "Analysis is still processing")
            }
            AnalysisJobStatus.FAILED -> {
                throw AnalysisProxyException(500, entity.errorMessage ?: "Analysis failed")
            }
            AnalysisJobStatus.COMPLETED -> Unit
        }

        val summaryKey = entity.summaryKey
            ?: throw AnalysisProxyException(500, "Analysis completed without a summary artifact")
        val storedSummary = readJsonObject(summaryKey, StoredAnalysisSummary::class.java)
        val resolvedResultsKey = entity.resultsKey ?: storedSummary.tilePredictionsKey
        val predictions = resolvedResultsKey?.let { readJsonList(it, tilePredictionListType) }.orEmpty()

        return AnalysisResultResponse(
            imageId = storedSummary.imageId,
            tileLevel = storedSummary.tileLevel,
            dzi = storedSummary.dzi,
            summary = storedSummary.summary,
            heatmapKey = entity.heatmapKey ?: storedSummary.heatmapKey,
            summaryKey = entity.summaryKey,
            resultsKey = resolvedResultsKey,
            timings = storedSummary.timings,
            tilePredictions = predictions
        )
    }

    fun updateJob(jobId: String, update: AnalysisJobEventUpdate): AnalysisStatusResponse {
        val entity = findJobOrThrow(jobId)

        entity.status = update.status
        entity.tileLevel = update.tileLevel ?: entity.tileLevel
        entity.threshold = update.threshold ?: entity.threshold
        entity.tissueThreshold = update.tissueThreshold ?: entity.tissueThreshold
        entity.tilesProcessed = update.tilesProcessed ?: entity.tilesProcessed
        entity.totalTiles = update.totalTiles ?: entity.totalTiles
        entity.statusMessage = update.message ?: entity.statusMessage ?: defaultMessageForStatus(update.status)
        entity.heatmapKey = update.heatmapKey ?: entity.heatmapKey
        entity.summaryKey = update.summaryKey ?: entity.summaryKey
        entity.resultsKey = update.resultsKey ?: entity.resultsKey
        entity.tumorAreaPercentage = update.tumorAreaPercentage ?: entity.tumorAreaPercentage
        entity.aggregateScore = update.aggregateScore ?: entity.aggregateScore
        entity.maxScore = update.maxScore ?: entity.maxScore
        entity.errorMessage = if (update.status == AnalysisJobStatus.FAILED) {
            update.errorMessage ?: entity.errorMessage
        } else {
            null
        }

        val saved = analysisJobRepository.save(entity)
        return AnalysisStatusResponse(
            status = saved.status.asApiValue(),
            imageId = saved.imageId,
            tileLevel = saved.tileLevel,
            tilesProcessed = saved.tilesProcessed,
            totalTiles = saved.totalTiles,
            message = saved.statusMessage ?: defaultMessageForStatus(saved.status)
        )
    }

    fun getHeatmapObjectForJob(jobId: String): InputStream {
        val entity = findJobOrThrow(jobId)
        if (entity.status == AnalysisJobStatus.PROCESSING) {
            throw AnalysisProxyException(202, entity.statusMessage ?: "Analysis is still processing")
        }
        if (entity.status == AnalysisJobStatus.FAILED) {
            throw AnalysisProxyException(500, entity.errorMessage ?: "Analysis failed")
        }

        val heatmapKey = entity.heatmapKey
            ?: throw AnalysisProxyException(404, "No heatmap key available for job $jobId")
        return getHeatmapObject(heatmapKey)
    }

    fun getHeatmapObject(heatmapKey: String): InputStream {
        return try {
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(minioProperties.buckets.tiles)
                    .key(heatmapKey)
                    .build()
            )
        } catch (_: NoSuchKeyException) {
            throw AnalysisProxyException(404, "Heatmap object not found: $heatmapKey")
        } catch (e: Exception) {
            throw AnalysisProxyException(500, "Failed to fetch heatmap object: ${e.message}")
        }
    }

    private fun markJobFailed(jobId: String, message: String) {
        analysisJobRepository.findByJobId(jobId).ifPresent { entity ->
            entity.status = AnalysisJobStatus.FAILED
            entity.statusMessage = "Analysis failed."
            entity.errorMessage = message.take(1024)
            analysisJobRepository.save(entity)
        }
    }

    private fun findJobOrThrow(jobId: String): AnalysisJobEntity {
        return analysisJobRepository.findByJobId(jobId)
            .orElseThrow { AnalysisProxyException(404, "Analysis job not found: $jobId") }
    }

    private fun defaultMessageForStatus(status: AnalysisJobStatus): String = when (status) {
        AnalysisJobStatus.PROCESSING -> "Analysis is in progress."
        AnalysisJobStatus.COMPLETED -> "Analysis complete."
        AnalysisJobStatus.FAILED -> "Analysis failed."
    }

    private fun AnalysisJobStatus.asApiValue(): String = when (this) {
        AnalysisJobStatus.PROCESSING -> "processing"
        AnalysisJobStatus.COMPLETED -> "completed"
        AnalysisJobStatus.FAILED -> "failed"
    }

    private fun <T> readJsonObject(objectKey: String, clazz: Class<T>): T {
        return s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(minioProperties.buckets.tiles)
                .key(objectKey)
                .build()
        ).use { input ->
            objectMapper.readValue(input, clazz)
        }
    }

    private fun <T> readJsonList(objectKey: String, typeReference: TypeReference<T>): T {
        return s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(minioProperties.buckets.tiles)
                .key(objectKey)
                .build()
        ).use { input ->
            objectMapper.readValue(input, typeReference)
        }
    }

    private fun toProxyException(operation: String, e: HttpStatusCodeException): AnalysisProxyException {
        val statusCode = e.statusCode.value()
        val body = e.responseBodyAsString.takeIf { it.isNotBlank() }
        val message = buildString {
            append("Analysis service ")
            append(operation)
            append(" failed (")
            append(statusCode)
            append(")")
            if (body != null) {
                append(": ")
                append(body)
            }
        }
        logger.error(message)
        return AnalysisProxyException(statusCode, message)
    }
}

package com.histoflow.backend.service

import com.fasterxml.jackson.annotation.JsonProperty
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
    private val analysisJobRepository: AnalysisJobRepository
) {
    private val logger = LoggerFactory.getLogger(AnalysisService::class.java)

    class AnalysisProxyException(
        val statusCode: Int,
        override val message: String
    ) : RuntimeException(message)

    data class AnalyzeRequest(
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
        val timings: Map<String, Double>? = null,
        @JsonProperty("tile_predictions")
        val tilePredictions: List<TilePredictionResponse> = emptyList()
    )

    fun triggerAnalysis(
        imageId: String,
        tileLevel: Int? = null,
        threshold: Float? = null,
        tissueThreshold: Float? = null,
        batchSize: Int? = null
    ): AnalyzeResponse {
        val url = "${analysisProperties.baseUrl}/jobs/analyze"
        val request = AnalyzeRequest(
            image_id = imageId,
            tile_level = tileLevel,
            threshold = threshold,
            tissue_threshold = tissueThreshold,
            batch_size = batchSize ?: 16
        )

        logger.info("Triggering analysis for image {} with request {} via {}", imageId, request, url)

        try {
            val response = restTemplate.postForObject(url, request, AnalyzeResponse::class.java)
            if (response == null) {
                throw AnalysisProxyException(502, "Empty response from analysis service")
            }

            // Save initial PROCESSING row to DB
            analysisJobRepository.save(
                AnalysisJobEntity(
                    jobId = response.job_id,
                    imageId = imageId,
                    status = AnalysisJobStatus.PROCESSING,
                    tileLevel = tileLevel,
                    threshold = threshold,
                    tissueThreshold = tissueThreshold
                )
            )
            logger.info("Saved analysis job {} to DB for image {}", response.job_id, imageId)

            return response
        } catch (e: HttpStatusCodeException) {
            throw toProxyException("trigger", e)
        } catch (e: AnalysisProxyException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to trigger analysis for image {}: {}", imageId, e.message)
            throw AnalysisProxyException(502, "Failed to reach analysis service: ${e.message}")
        }
    }

    fun getStatus(jobId: String): AnalysisStatusResponse {
        val url = "${analysisProperties.baseUrl}/jobs/$jobId/status"
        try {
            val response = restTemplate.getForObject(url, AnalysisStatusResponse::class.java)
            if (response == null) {
                throw AnalysisProxyException(502, "Empty status response from analysis service")
            }

            // Update progress and failure state in DB (only while still PROCESSING)
            analysisJobRepository.findByJobId(jobId).ifPresent { entity ->
                if (entity.status == AnalysisJobStatus.PROCESSING) {
                    entity.tilesProcessed = response.tilesProcessed
                    entity.totalTiles = response.totalTiles
                    if (response.tileLevel != null) entity.tileLevel = response.tileLevel
                    if (response.status == "failed") {
                        entity.status = AnalysisJobStatus.FAILED
                        entity.errorMessage = response.message
                    }
                    analysisJobRepository.save(entity)
                }
            }

            return response
        } catch (e: HttpStatusCodeException) {
            throw toProxyException("status", e)
        } catch (e: AnalysisProxyException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to get analysis status for job {}: {}", jobId, e.message)
            throw AnalysisProxyException(502, "Failed to reach analysis service: ${e.message}")
        }
    }

    fun getResults(jobId: String): AnalysisResultResponse {
        val url = "${analysisProperties.baseUrl}/jobs/$jobId/results"
        try {
            val response = restTemplate.getForObject(url, AnalysisResultResponse::class.java)
            if (response == null) {
                throw AnalysisProxyException(502, "Empty results response from analysis service")
            }

            // Update DB row with completed state and summary stats
            analysisJobRepository.findByJobId(jobId).ifPresent { entity ->
                entity.status = AnalysisJobStatus.COMPLETED
                entity.tileLevel = response.tileLevel
                entity.heatmapKey = response.heatmapKey
                response.summary?.let { summary ->
                    entity.tumorAreaPercentage = summary.tumorAreaPercentage
                    entity.aggregateScore = summary.aggregateScore
                    entity.maxScore = summary.maxScore
                    entity.totalTiles = summary.totalTiles
                    entity.tilesProcessed = summary.tissueTiles
                }
                analysisJobRepository.save(entity)
                logger.info("Updated job {} to COMPLETED in DB", jobId)
            }

            return response
        } catch (e: HttpStatusCodeException) {
            throw toProxyException("results", e)
        } catch (e: AnalysisProxyException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to get analysis results for job {}: {}", jobId, e.message)
            throw AnalysisProxyException(502, "Failed to reach analysis service: ${e.message}")
        }
    }

    data class AnalysisJobSummary(
        val jobId: String,
        val imageId: String,
        val status: String,
        val tileLevel: Int?,
        val threshold: Float?,
        val tissueThreshold: Float?,
        val tilesProcessed: Int,
        val totalTiles: Int,
        val heatmapKey: String?,
        val tumorAreaPercentage: Double?,
        val aggregateScore: Double?,
        val maxScore: Double?,
        val errorMessage: String?,
        val createdAt: String,
        val updatedAt: String
    )

    fun getHeatmapKey(jobId: String): String {
        val entity = analysisJobRepository.findByJobId(jobId).orElse(null)
        if (entity?.heatmapKey != null) {
            return entity.heatmapKey!!
        }
        // Fall back to live service if not yet persisted
        return getResults(jobId).heatmapKey
            ?: throw AnalysisProxyException(404, "No heatmap key available for job $jobId")
    }

    fun getAnalysisByImageId(imageId: String): List<AnalysisJobSummary> {
        return analysisJobRepository.findAllByImageId(imageId)
            .sortedByDescending { it.createdAt }
            .map { entity ->
                AnalysisJobSummary(
                    jobId = entity.jobId,
                    imageId = entity.imageId,
                    status = entity.status.name,
                    tileLevel = entity.tileLevel,
                    threshold = entity.threshold,
                    tissueThreshold = entity.tissueThreshold,
                    tilesProcessed = entity.tilesProcessed,
                    totalTiles = entity.totalTiles,
                    heatmapKey = entity.heatmapKey,
                    tumorAreaPercentage = entity.tumorAreaPercentage,
                    aggregateScore = entity.aggregateScore,
                    maxScore = entity.maxScore,
                    errorMessage = entity.errorMessage,
                    createdAt = entity.createdAt.toString(),
                    updatedAt = entity.updatedAt.toString()
                )
            }
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

package com.histoflow.backend.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.histoflow.backend.config.MinioProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

@Service
class TileService(
    private val s3Client: S3Client,
    private val minioProps: MinioProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val UUID_REGEX = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }

    /**
     * Fetch DZI descriptor XML file from MinIO
     * MinIO path: {imageId}/image.dzi
     */
    fun getDziDescriptor(imageId: String): ResponseInputStream<GetObjectResponse> {
        val objectKey = "$imageId/image.dzi"
        logger.debug("Fetching DZI descriptor: bucket={}, key={}", minioProps.buckets.tiles, objectKey)

        return try {
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(minioProps.buckets.tiles)
                    .key(objectKey)
                    .build()
            )
        } catch (e: NoSuchKeyException) {
            logger.error("DZI descriptor not found: {}", objectKey)
            throw IllegalArgumentException("Image $imageId not found or tiles not generated")
        }
    }

    fun getTilingStatus(imageId: String): TilingStatus {
        val descriptorKey = "$imageId/image.dzi"
        return try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(minioProps.buckets.tiles)
                    .key(descriptorKey)
                    .build()
            )

            TilingStatus(
                imageId = imageId,
                status = "completed",
                message = "Tiles are ready"
            )
        } catch (_: NoSuchKeyException) {
            val rawExists = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(minioProps.buckets.uploads)
                    .prefix("$imageId/")
                    .maxKeys(1)
                    .build()
            ).contents().isNotEmpty()

            if (rawExists) {
                TilingStatus(
                    imageId = imageId,
                    status = "processing",
                    message = "Tiling in progress"
                )
            } else {
                TilingStatus(
                    imageId = imageId,
                    status = "not_found",
                    message = "Upload not found"
                )
            }
        }
    }

    /**
     * Fetch individual tile JPEG from MinIO
     * MinIO path: {imageId}/image_files/{level}/{x}_{y}.jpg
     */
    fun getTile(imageId: String, level: Int, x: Int, y: Int): ResponseInputStream<GetObjectResponse> {
        val objectKey = "$imageId/image_files/$level/${x}_$y.jpg"
        logger.debug("Fetching tile: bucket={}, key={}", minioProps.buckets.tiles, objectKey)

        return try {
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(minioProps.buckets.tiles)
                    .key(objectKey)
                    .build()
            )
        } catch (e: NoSuchKeyException) {
            logger.error("Tile not found: {}", objectKey)
            throw IllegalArgumentException("Tile not found: level=$level, x=$x, y=$y")
        }
    }

    fun listDatasets(limit: Int = 100, continuationToken: String? = null, prefix: String? = null): DatasetPage {
        val sanitizedLimit = limit.coerceIn(1, 50)
        val originalQuery = prefix?.trim()?.takeIf { it.isNotEmpty() }
        val searchTerm = originalQuery?.lowercase()

        // Use delimiter listing to discover dataset IDs in O(datasets) instead of O(tiles).
        // S3 returns top-level "folders" as commonPrefixes without scanning every tile file.
        val datasetIds = mutableListOf<String>()
        var token = continuationToken
        var nextToken: String? = null

        while (true) {
            val requestBuilder = ListObjectsV2Request.builder()
                .bucket(minioProps.buckets.tiles)
                .delimiter("/")
                .maxKeys(1000)

            if (!token.isNullOrBlank()) {
                requestBuilder.continuationToken(token)
            }

            val response = try {
                s3Client.listObjectsV2(requestBuilder.build())
            } catch (_: NoSuchBucketException) {
                logger.warn("Tiles bucket '{}' does not exist yet; returning empty dataset page", minioProps.buckets.tiles)
                return DatasetPage(datasets = emptyList(), nextContinuationToken = null, appliedPrefix = originalQuery.orEmpty())
            }

            response.commonPrefixes()
                .mapNotNull { it.prefix()?.trimEnd('/') }
                .filter { it.isNotBlank() && UUID_REGEX.matches(it) }
                .forEach { datasetIds.add(it) }

            val isTruncated = response.isTruncated
            nextToken = response.nextContinuationToken()

            if (!isTruncated) break
            if (searchTerm == null && datasetIds.size >= sanitizedLimit) break

            token = nextToken
        }

        val summaries = datasetIds
            .mapNotNull { datasetId ->
                val meta = loadDatasetMetadata(datasetId)
                val datasetName = meta?.datasetName?.takeIf { it.isNotBlank() } ?: datasetId
                if (searchTerm != null && !matchesSearch(datasetId, datasetName, searchTerm)) return@mapNotNull null
                DatasetSummary(
                    imageId = datasetId,
                    datasetName = datasetName,
                    totalObjects = meta?.tileFileCount?.toLong() ?: 0L,
                    totalSizeBytes = meta?.tileTotalSizeBytes ?: 0L,
                    lastModifiedMillis = meta?.generatedAt?.let { parseGeneratedAt(it) } ?: 0L
                )
            }
            .sortedByDescending { it.lastModifiedMillis }
            .take(sanitizedLimit)

        return DatasetPage(
            datasets = summaries,
            nextContinuationToken = if (datasetIds.size >= sanitizedLimit) nextToken else null,
            appliedPrefix = originalQuery.orEmpty()
        )
    }

    private fun loadDatasetMetadata(imageId: String): TileMetadata? {
        val metadataKey = "$imageId/metadata.json"
        return try {
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(minioProps.buckets.tiles)
                    .key(metadataKey)
                    .build()
            ).use { input -> objectMapper.readValue(input, TileMetadata::class.java) }
        } catch (_: NoSuchKeyException) {
            logger.debug("Metadata not found for imageId={}", imageId)
            null
        } catch (e: Exception) {
            logger.warn("Failed to read metadata for imageId={}", imageId, e)
            null
        }
    }

    private fun parseGeneratedAt(value: String): Long {
        return try {
            java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun matchesSearch(imageId: String, datasetName: String?, searchTerm: String?): Boolean {
        if (searchTerm.isNullOrBlank()) {
            return true
        }

        val lowerTerm = searchTerm.lowercase()
        if (imageId.lowercase().contains(lowerTerm)) {
            return true
        }

        return datasetName?.lowercase()?.contains(lowerTerm) == true
    }
}

data class DatasetSummary(
    val imageId: String,
    val datasetName: String,
    val totalObjects: Long,
    val totalSizeBytes: Long,
    val lastModifiedMillis: Long
)

data class DatasetPage(
    val datasets: List<DatasetSummary>,
    val nextContinuationToken: String?,
    val appliedPrefix: String
)

data class TilingStatus(
    val imageId: String,
    val status: String,
    val message: String? = null
)


@JsonIgnoreProperties(ignoreUnknown = true)
private data class TileMetadata(
    @JsonProperty("dataset_name")
    val datasetName: String?,
    @JsonProperty("tile_file_count")
    val tileFileCount: Int?,
    @JsonProperty("tile_total_size_bytes")
    val tileTotalSizeBytes: Long?,
    @JsonProperty("generated_at")
    val generatedAt: String?
)

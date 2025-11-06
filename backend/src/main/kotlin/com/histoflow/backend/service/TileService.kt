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
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

@Service
class TileService(
    private val s3Client: S3Client,
    private val minioProps: MinioProperties,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
                    .bucket(minioProps.buckets.raw)
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

        val aggregated = mutableMapOf<String, DatasetAccumulator>()

        var token = continuationToken
        var nextToken: String? = null

        while (true) {
            val requestBuilder = ListObjectsV2Request.builder()
                .bucket(minioProps.buckets.tiles)
                .maxKeys(1000)

            if (!token.isNullOrBlank()) {
                requestBuilder.continuationToken(token)
            }

            val response = s3Client.listObjectsV2(requestBuilder.build())

            response.contents()
                .asSequence()
                .mapNotNull { obj ->
                    val key = obj.key()
                    val datasetId = key.substringBefore('/', "")
                    if (datasetId.isBlank()) {
                        null
                    } else {
                        datasetId to obj
                    }
                }
                .forEach { (datasetId, obj) ->
                    val accumulator = aggregated.getOrPut(datasetId) { DatasetAccumulator() }
                    accumulator.totalObjects += 1
                    accumulator.totalSizeBytes += obj.size()
                    accumulator.lastModifiedMillis = maxOf(accumulator.lastModifiedMillis, obj.lastModified().toEpochMilli())
                }

            aggregated.forEach { (datasetId, accumulator) ->
                if (!accumulator.metadataLoaded) {
                    accumulator.datasetName = loadDatasetName(datasetId) ?: datasetId
                    accumulator.metadataLoaded = true
                }
                accumulator.matchesQuery = matchesSearch(datasetId, accumulator.datasetName, searchTerm)
            }

            val matchedCount = aggregated.count { it.value.matchesQuery }
            val isTruncated = response.isTruncated
            val candidateNext = response.nextContinuationToken()

            val shouldContinue = when {
                !isTruncated -> false
                searchTerm == null -> aggregated.size < sanitizedLimit
                else -> matchedCount < sanitizedLimit
            }

            if (!shouldContinue) {
                nextToken = candidateNext
                break
            }

            if (candidateNext.isNullOrBlank()) {
                nextToken = null
                break
            }

            token = candidateNext
        }

        val summaries = aggregated
            .filter { (_, accumulator) -> searchTerm == null || accumulator.matchesQuery }
            .map { (imageId, accumulator) ->
                DatasetSummary(
                    imageId = imageId,
                    datasetName = accumulator.datasetName ?: imageId,
                    totalObjects = accumulator.totalObjects,
                    totalSizeBytes = accumulator.totalSizeBytes,
                    lastModifiedMillis = accumulator.lastModifiedMillis
                )
            }
            .sortedByDescending { it.lastModifiedMillis }
            .take(sanitizedLimit)

        return DatasetPage(
            datasets = summaries,
            nextContinuationToken = nextToken,
            appliedPrefix = originalQuery.orEmpty()
        )
    }

    private fun loadDatasetName(imageId: String): String? {
        val metadataKey = "$imageId/metadata.json"
        return try {
            s3Client.getObject(
                GetObjectRequest.builder()
                    .bucket(minioProps.buckets.tiles)
                    .key(metadataKey)
                    .build()
            ).use { input ->
                val metadata = objectMapper.readValue(input, TileMetadata::class.java)
                metadata.datasetName?.takeIf { it.isNotBlank() }
            }
        } catch (_: NoSuchKeyException) {
            logger.debug("Metadata not found for imageId={}", imageId)
            null
        } catch (e: Exception) {
            logger.warn("Failed to read metadata for imageId={}", imageId, e)
            null
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

private data class DatasetAccumulator(
    var totalObjects: Long = 0,
    var totalSizeBytes: Long = 0,
    var lastModifiedMillis: Long = 0,
    var datasetName: String? = null,
    var metadataLoaded: Boolean = false,
    var matchesQuery: Boolean = true
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class TileMetadata(
    @JsonProperty("dataset_name")
    val datasetName: String?
)

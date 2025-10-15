package com.histoflow.backend.service

import com.histoflow.backend.config.MinioProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

@Service
class TileService(
    private val s3Client: S3Client,
    private val minioProps: MinioProperties
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
        val aggregated = mutableMapOf<String, DatasetSummary>()

        var token = continuationToken
        var nextToken: String? = null

        while (aggregated.size < limit) {
            val requestBuilder = ListObjectsV2Request.builder()
                .bucket(minioProps.buckets.tiles)
                .maxKeys(1000)

            if (!token.isNullOrBlank()) {
                requestBuilder.continuationToken(token)
            }

            if (!prefix.isNullOrBlank()) {
                requestBuilder.prefix(prefix)
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
                    val summary = aggregated.getOrPut(datasetId) {
                        DatasetSummary(
                            imageId = datasetId,
                            totalObjects = 0,
                            totalSizeBytes = 0,
                            lastModifiedMillis = 0
                        )
                    }

                    aggregated[datasetId] = summary.copy(
                        totalObjects = summary.totalObjects + 1,
                        totalSizeBytes = summary.totalSizeBytes + obj.size(),
                        lastModifiedMillis = maxOf(summary.lastModifiedMillis, obj.lastModified().toEpochMilli())
                    )
                }

            nextToken = response.nextContinuationToken()

            if (nextToken.isNullOrBlank() || aggregated.size >= limit) {
                break
            } else {
                token = nextToken
            }
        }

        val sorted = aggregated.values
            .sortedByDescending { it.lastModifiedMillis }

        return DatasetPage(
            datasets = sorted.take(limit),
            nextContinuationToken = nextToken,
            appliedPrefix = prefix.orEmpty()
        )
    }
}

data class DatasetSummary(
    val imageId: String,
    val totalObjects: Long,
    val totalSizeBytes: Long,
    val lastModifiedMillis: Long
)

data class DatasetPage(
    val datasets: List<DatasetSummary>,
    val nextContinuationToken: String?,
    val appliedPrefix: String
)

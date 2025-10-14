package com.histoflow.backend.service

import com.histoflow.backend.config.MinioProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
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
}

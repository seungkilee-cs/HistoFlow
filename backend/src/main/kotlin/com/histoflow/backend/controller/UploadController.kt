package com.histoflow.backend.controller

import io.minio.GetPresignedObjectUrlArgs
import io.minio.MinioClient
import io.minio.MakeBucketArgs
import io.minio.BucketExistsArgs
import com.histoflow.backend.config.MinioProperties
import com.histoflow.backend.service.TilingTriggerService
import io.minio.http.Method
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.TimeUnit

// data class for the request body from the frontend
data class InitiateUploadRequest(
    val fileName: String,
    val contentType: String,
    val datasetName: String? = null
)

// data class for the response body sent to the frontend
data class InitiateUploadResponse(
    val uploadUrl: String,
    val objectName: String,
    val imageId: String,
    val datasetName: String
)

data class CompleteUploadRequest(
    val objectName: String,
    val imageId: String? = null,
    val datasetName: String? = null
)

data class CompleteUploadResponse(
    val status: String,
    val imageId: String,
    val message: String
)

@RestController
@RequestMapping("/api/v1/uploads")
class UploadController(
    private val minioClient: MinioClient,
    private val tilingTriggerService: TilingTriggerService,
    private val minioProperties: MinioProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // This is the endpoint the frontend will call to get the pre-signed URL.
    @PostMapping("/initiate")
    fun initiateUpload(@RequestBody request: InitiateUploadRequest): ResponseEntity<InitiateUploadResponse> {
        try {
            // Define the bucket where the raw file will be temporarily stored
            val bucketName = minioProperties.buckets.raw
            logger.info("InitiateUpload request: fileName='{}', contentType='{}', datasetName='{}', bucket='{}'",
                request.fileName,
                request.contentType,
                request.datasetName,
                bucketName
            )

            // best practice - create a unique ID for the object to avoid name collisions
            val imageId = UUID.randomUUID().toString()
            val objectName = "$imageId/${request.fileName}"
            val datasetName = request.datasetName?.takeIf { it.isNotBlank() } ?: request.fileName

            // check if the bucket exists and create it if it doesn't. In prod, we prob want to do this upon start
            val bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build()
            )
            if (!bucketExists) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build()
                )
                println("Bucket '$bucketName' created.")
            }

            // logic to generate the pre-signed URL
            println("Generating pre-signed URL for object: $objectName")
            val presignedUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT) // grant permission to upload (PUT) a file
                    .bucket(bucketName)
                    . `object`(objectName)
                    .expiry(15, TimeUnit.MINUTES) // url will be valid for arbitrary amount of itme. 15 minutes for now -> may need to check on the timeout for the larger files? -> seems like that according to the docs. So we may need to think about multi part upload. For now, test with 200MB files for poc.
                    .build()
            )
            println("Successfully generated URL.")

            // once the url is generated, send it back to the frontend in the response body
            val response = InitiateUploadResponse(
                uploadUrl = presignedUrl,
                objectName = objectName,
                imageId = imageId,
                datasetName = datasetName
            )
            logger.info(
                "InitiateUpload response: imageId='{}', objectName='{}', datasetName='{}'",
                response.imageId,
                response.objectName,
                response.datasetName
            )
            return ResponseEntity.ok(response)

        } catch (e: Exception) {
            println("Error generating pre-signed URL: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/complete")
    fun completeUpload(@RequestBody request: CompleteUploadRequest): ResponseEntity<CompleteUploadResponse> {
        return try {
            val resolvedImageId = request.imageId ?: request.objectName.substringBefore('/')

            logger.info(
                "CompleteUpload request: imageId='{}', objectName='{}', datasetName='{}'",
                resolvedImageId,
                request.objectName,
                request.datasetName
            )

            tilingTriggerService.triggerTiling(
                imageId = resolvedImageId,
                sourceBucket = minioProperties.buckets.raw,
                sourceObjectName = request.objectName,
                datasetName = request.datasetName
            )

            val response = CompleteUploadResponse(
                status = "accepted",
                imageId = resolvedImageId,
                message = "Tiling job initiated"
            )
            logger.info(
                "CompleteUpload response: status='{}', imageId='{}', message='{}'",
                response.status,
                response.imageId,
                response.message
            )

            ResponseEntity.ok(
                response
            )
        } catch (e: Exception) {
            logger.error(
                "CompleteUpload failed: imageId='{}', objectName='{}', datasetName='{}'",
                request.imageId,
                request.objectName,
                request.datasetName,
                e
            )
            ResponseEntity.internalServerError().body(
                CompleteUploadResponse(
                    status = "error",
                    imageId = request.imageId.orEmpty(),
                    message = "Failed to initiate tiling: ${e.message}"
                )
            )
        }
    }
}

package com.histoflow.backend.controller

import com.histoflow.backend.config.MinioProperties
import com.histoflow.backend.service.TilingTriggerService
import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.http.Method
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Request body for initiating an upload
 * 
 * @property fileName Name of the file to upload
 * @property contentType MIME type of the file
 * @property datasetName Optional friendly name for the dataset
 */
data class InitiateUploadRequest(
    val fileName: String,
    val contentType: String,
    val datasetName: String? = null
)

/**
 * Response body for upload initiation
 * 
 * @property uploadUrl Pre-signed URL for uploading to MinIO
 * @property objectName Full object path in MinIO (imageId/fileName)
 * @property imageId Unique identifier for the image
 * @property datasetName Resolved dataset name (filename if not provided)
 */
data class InitiateUploadResponse(
    val uploadUrl: String,
    val objectName: String,
    val imageId: String,
    val datasetName: String
)

/**
 * Request body for completing an upload
 * 
 * @property objectName Full object path in MinIO
 * @property imageId Optional image ID (extracted from objectName if not provided)
 * @property datasetName Optional dataset name
 */
data class CompleteUploadRequest(
    val objectName: String,
    val imageId: String? = null,
    val datasetName: String? = null
)

/**
 * Response body for upload completion
 * 
 * @property status Status of the request (accepted, error)
 * @property imageId Image identifier
 * @property message Human-readable status message
 */
data class CompleteUploadResponse(
    val status: String,
    val imageId: String,
    val message: String
)

/**
 * Controller handling file upload workflow
 * 
 * Implements the upload-first pattern:
 * 1. Frontend requests pre-signed URL (/initiate)
 * 2. Frontend uploads directly to MinIO
 * 3. Frontend notifies backend of completion (/complete)
 * 4. Backend triggers tiling microservice
 */
@RestController
@RequestMapping("/api/v1/uploads")
class UploadController(
    private val minioClient: MinioClient,
    private val tilingTriggerService: TilingTriggerService,
    private val minioProperties: MinioProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Initiate upload by generating a pre-signed URL
     * 
     * This endpoint:
     * - Generates a unique image ID
     * - Creates the bucket if it doesn't exist
     * - Returns a pre-signed URL valid for 15 minutes
     * 
     * @param request Upload initiation request
     * @return Pre-signed URL and metadata
     */
    @PostMapping("/initiate")
    fun initiateUpload(
        @RequestBody request: InitiateUploadRequest
    ): ResponseEntity<InitiateUploadResponse> {
        return try {
            // Define the bucket where the raw file will be temporarily stored (from config)
            val bucketName = props.buckets.uploads
            
            logger.info(
                "InitiateUpload request: fileName='{}', contentType='{}', datasetName='{}', bucket='{}'",
                request.fileName,
                request.contentType,
                request.datasetName,
                bucketName
            )
            // Generate unique ID for the object to avoid name collisions
            val imageId = UUID.randomUUID().toString()
            val objectName = "$imageId/${request.fileName}"
            val datasetName = request.datasetName?.takeIf { it.isNotBlank() } ?: request.fileName

            // Ensure bucket exists (in production, we do this at startup)
            ensureBucketExists(bucketName)

            // Generate pre-signed URL for PUT operation
            logger.debug("Generating pre-signed URL for object: {}", objectName)
            val presignedUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)  // Grant permission to upload
                    .bucket(bucketName)
                    .`object`(objectName)
                    .expiry(15, TimeUnit.MINUTES)  // URL valid for 15 minutes
                    .build()
            )
            logger.debug("Successfully generated pre-signed URL")

            // Return response with pre-signed URL and metadata
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
            
            ResponseEntity.ok(response)

        } catch (e: Exception) {
            logger.error("Failed to initiate upload: {}", e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    InitiateUploadResponse(
                        uploadUrl = "",
                        objectName = "",
                        imageId = "",
                        datasetName = ""
                    )
                )
        }
    }

    /**
     * Complete upload and trigger tiling
     * 
     * This endpoint:
     * - Receives notification that upload to MinIO completed
     * - Triggers the tiling microservice asynchronously
     * - Returns immediately (tiling happens in background)
     * 
     * @param request Upload completion notification
     * @return Acceptance confirmation
     */
    @PostMapping("/complete")
    fun completeUpload(
        @RequestBody request: CompleteUploadRequest
    ): ResponseEntity<CompleteUploadResponse> {
        val resolvedImageId = request.imageId ?: request.objectName.substringBefore('/')
        
        logger.info(
            "CompleteUpload request: imageId='{}', objectName='{}', datasetName='{}'",
            resolvedImageId,
            request.objectName,
            request.datasetName
        )

        return try {
            // ✅ Add null check for bucket configuration
            val rawBucket = minioProperties.buckets.raw
                ?: throw IllegalStateException("MinIO raw bucket not configured")

            logger.debug("Using raw bucket: {}", rawBucket)

            // Trigger tiling microservice
            tilingTriggerService.triggerTiling(
                imageId = resolvedImageId,
                sourceBucket = rawBucket,
                sourceObjectName = request.objectName,
                datasetName = request.datasetName
            )

            // Return success response
            val response = CompleteUploadResponse(
                status = "accepted",
                imageId = resolvedImageId,
                message = "Tiling job initiated successfully"
            )
            
            logger.info(
                "[O] CompleteUpload success: imageId='{}', status='{}'",
                response.imageId,
                response.status
            )

            ResponseEntity.ok(response)

        } catch (e: IllegalStateException) {
            // Tiling service call failed or configuration error
            logger.error(
                "[X] Tiling trigger failed: imageId='{}', error='{}'",
                resolvedImageId,
                e.message,
                e
            )
            
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CompleteUploadResponse(
                    status = "error",
                    imageId = resolvedImageId,
                    message = "Failed to initiate tiling: ${e.message}"
                )
            )

        } catch (e: Exception) {
            // Unexpected error
            logger.error(
                "[X] Unexpected error in CompleteUpload: imageId='{}'",
                resolvedImageId,
                e
            )
            
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CompleteUploadResponse(
                    status = "error",
                    imageId = resolvedImageId,
                    message = "Internal server error: ${e.message}"
                )
            )
        }
    }


    /**
     * Ensure MinIO bucket exists, create if not
     * 
     * @param bucketName Name of the bucket to check/create
     */
    private fun ensureBucketExists(bucketName: String) {
        val exists = minioClient.bucketExists(
            BucketExistsArgs.builder()
                .bucket(bucketName)
                .build()
        )
        
        if (!exists) {
            logger.info("Creating bucket: {}", bucketName)
            minioClient.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build()
            )
            logger.info("Bucket '{}' created successfully", bucketName)
        } else {
            logger.debug("Bucket '{}' already exists", bucketName)
        }
    }
}

package com.histoflow.backend.controller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.histoflow.backend.service.UploadService
import com.histoflow.backend.service.TilingTriggerService
import com.histoflow.backend.service.TilingJobService
import com.histoflow.backend.config.MinioProperties
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.slf4j.LoggerFactory

// Request/response DTOs for multipart API
@JsonIgnoreProperties(ignoreUnknown = true)
data class InitiateMultipartRequest(
    val filename: String,
    val contentType: String? = null,
    val partSizeHint: Long? = null,
    val datasetName: String? = null
)

data class InitiateMultipartResponse(
    val uploadId: String,
    val key: String,
    val partSize: Long,
    val imageId: String,
    val datasetName: String
)

data class PresignRequest(
    val uploadId: String,
    val key: String,
    val partNumbers: List<Int>
)

data class PartUrl(val partNumber: Int, val url: String)
data class PresignResponse(val urls: List<PartUrl>)

data class CompleteRequestPart(val partNumber: Int, val etag: String)
data class CompleteRequest(
    val uploadId: String, 
    val key: String, 
    val parts: List<CompleteRequestPart>,
    val datasetName: String? = null
)

data class CompleteMultipartResponse(
    val jobId: String,
    val imageId: String,
    val datasetName: String,
    val status: String,
    val message: String
)

data class AbortRequest(val uploadId: String, val key: String)


@RestController
@RequestMapping("/api/v1/uploads/multipart")
class MultipartUploadController(
    private val uploadService: UploadService,
    private val tilingJobService: TilingJobService,
    private val tilingTriggerService: TilingTriggerService,
    private val minioProperties: MinioProperties
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val uploadKeyPattern = Regex("""^uploads/([0-9a-fA-F-]{36})-(.+)$""")

    @PostMapping("/initiate")
    fun initiate(@RequestBody req: InitiateMultipartRequest): ResponseEntity<InitiateMultipartResponse> {
        return try {
            val result = uploadService.initiateMultipartUpload(
                req.filename,
                req.contentType,
                req.partSizeHint,
                req.datasetName
            )
            ResponseEntity.ok(
                InitiateMultipartResponse(
                    result.uploadId,
                    result.key,
                    result.partSize,
                    result.imageId,
                    result.datasetName
                )
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/presign")
    fun presign(@RequestBody req: PresignRequest): ResponseEntity<PresignResponse> {
        return try {
            val urls = req.partNumbers.map { partNumber ->
                val url = uploadService.presignUploadPart(req.uploadId, req.key, partNumber)
                PartUrl(partNumber, url)
            }
            ResponseEntity.ok(PresignResponse(urls))
        } catch (ex: Exception) {
            ex.printStackTrace()
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/complete")
    fun complete(@RequestBody req: CompleteRequest): ResponseEntity<Any> {
        return try {
            // complete multipart upload to minio
            val parts = req.parts.map { Pair(it.partNumber, it.etag) }
            uploadService.completeMultipartUpload(req.uploadId, req.key, parts)

            val imageId = extractImageId(req.key)
            val datasetName = req.datasetName?.takeIf { it.isNotBlank() } ?: extractDatasetName(req.key)
            val job = tilingJobService.createQueuedJob(imageId, datasetName)
            logger.info("Multipart upload complete: imageId={}, key={}", imageId, req.key)

            tilingTriggerService.triggerTiling(
                imageId = imageId,
                sourceBucket = minioProperties.buckets.uploads,
                sourceObjectName = req.key,
                datasetName = datasetName,
                jobId = job.id
            )
            
            logger.info("Tiling job triggered for imageId={}", imageId)

            ResponseEntity.ok(
                CompleteMultipartResponse(
                    jobId = job.id.toString(),
                    imageId = imageId,
                    datasetName = datasetName,
                    status = "accepted",
                    message = "Upload complete. Waiting for tiling worker."
                )
            )

        } catch (ex: Exception) {
            ex.printStackTrace()
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/abort")
    fun abort(@RequestBody req: AbortRequest): ResponseEntity<Any> {
        return try {
            uploadService.abortMultipartUpload(req.uploadId, req.key)
            ResponseEntity.ok().build()
        } catch (ex: Exception) {
            ex.printStackTrace()
            ResponseEntity.internalServerError().build()
        }
    }

    // image id fallback
    private fun extractImageId(key: String): String {
        val match = uploadKeyPattern.find(key)
        if (match != null) {
            return match.groupValues[1]
        }

        if (key.contains('/')) {
            return key.substringBefore('/')
        }

        return key.substringBeforeLast('-', missingDelimiterValue = key)
    }

    // dataset name fallback
    private fun extractDatasetName(key: String): String {
        val match = uploadKeyPattern.find(key)
        if (match != null) {
            return match.groupValues[2]
        }

        // fallback: strip prefix, leave filename as-is
        return key.substringAfterLast('/')
    }

}

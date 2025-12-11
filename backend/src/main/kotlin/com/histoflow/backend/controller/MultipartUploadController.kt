package com.histoflow.backend.controller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.histoflow.backend.service.UploadService
import com.histoflow.backend.service.TilingTriggerService
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
    val partSizeHint: Long? = null
)

data class InitiateMultipartResponse(
    val uploadId: String,
    val key: String,
    val partSize: Long
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

data class AbortRequest(val uploadId: String, val key: String)


@RestController
@RequestMapping("/api/v1/uploads/multipart")
class MultipartUploadController(
    private val uploadService: UploadService,
    private val tilingTriggerService: TilingTriggerService,
    private val minioProperties: MinioProperties
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val uploadKeyPattern = Regex("""^uploads/([0-9a-fA-F-]{36})-(.+)$""")

    @PostMapping("/initiate")
    fun initiate(@RequestBody req: InitiateMultipartRequest): ResponseEntity<InitiateMultipartResponse> {
        return try {
            val result = uploadService.initiateMultipartUpload(req.filename, req.contentType, req.partSizeHint)
            ResponseEntity.ok(InitiateMultipartResponse(result.uploadId, result.key, result.partSize))
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

            // extract image id for the tiling call
            val imageId = extractImageId(req.key)
            val datasetName = extractDatasetName(req.key)
            logger.info("Multipart upload complete: imageId={}, key={}", imageId, req.key)

            // trigger tiling
            tilingTriggerService.triggerTiling(
                imageId = imageId,
                sourceBucket = minioProperties.buckets.uploads,
                sourceObjectName = req.key,
                // ultimately want to pass this in from frontend or fall back on the file name, possibly add timestamp
                datasetName = datasetName
            )
            
            logger.info("Tiling job triggered for imageId={}", imageId)

            ResponseEntity.ok().build()

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

        // fallback: strip prefix and any trailing filename separator
        return key.substringAfterLast('/')
            .substringBeforeLast('-', missingDelimiterValue = key)
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

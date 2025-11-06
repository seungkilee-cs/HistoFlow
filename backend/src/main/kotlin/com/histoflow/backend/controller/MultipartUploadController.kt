package com.histoflow.backend.controller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.histoflow.backend.service.UploadService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
data class CompleteRequest(val uploadId: String, val key: String, val parts: List<CompleteRequestPart>)

data class AbortRequest(val uploadId: String, val key: String)


@RestController
@RequestMapping("/api/v1/uploads/multipart")
class MultipartUploadController(private val uploadService: UploadService) {

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
            val parts = req.parts.map { Pair(it.partNumber, it.etag) }
            uploadService.completeMultipartUpload(req.uploadId, req.key, parts)
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

}

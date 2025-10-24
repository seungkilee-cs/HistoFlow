package com.histoflow.backend.controller

import com.histoflow.backend.service.UploadService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.lang.Exception

data class InitiateRequest(val filename: String, val size: Long, val contentType: String?, val partSizeHint: Long?)
data class InitiateResponse(val uploadId: String, val key: String, val partSize: Long)

data class PresignRequest(val uploadId: String, val key: String, val partNumbers: List<Int>)
data class PresignUrl(val partNumber: Int, val url: String)
data class PresignResponse(val urls: List<PresignUrl>)

data class CompletePart(val partNumber: Int, val etag: String)
data class CompleteRequest(val uploadId: String, val key: String, val parts: List<CompletePart>)

data class AbortRequest(val uploadId: String, val key: String)

@RestController
@RequestMapping("/api/v1/uploads")
class UploadController(private val uploadService: UploadService) {

    @PostMapping("/initiate")
    fun initiate(@RequestBody req: InitiateRequest): ResponseEntity<InitiateResponse> {
        val res = uploadService.initiateMultipartUpload(req.filename, req.contentType, req.partSizeHint)
        return ResponseEntity.ok(InitiateResponse(res.uploadId, res.key, res.partSize))
    }

    @PostMapping("/presign")
    fun presign(@RequestBody req: PresignRequest): ResponseEntity<PresignResponse> {
        val urls = req.partNumbers.map { part ->
            val url = uploadService.presignUploadPart(req.uploadId, req.key, part)
            PresignUrl(part, url)
        }
        return ResponseEntity.ok(PresignResponse(urls))
    }

    @PostMapping("/complete")
    fun complete(@RequestBody req: CompleteRequest): ResponseEntity<Any> {
        val parts = req.parts.map { Pair(it.partNumber, it.etag) }
        uploadService.completeMultipartUpload(req.uploadId, req.key, parts)
        return ResponseEntity.ok(mapOf("key" to req.key))
    }

    @PostMapping("/abort")
    fun abort(@RequestBody req: AbortRequest): ResponseEntity<Any> {
        return try {
            uploadService.abortMultipartUpload(req.uploadId, req.key)
            ResponseEntity.ok(mapOf("aborted" to true))
        } catch (ex: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to (ex.message ?: "unknown")))
        }
    }
}

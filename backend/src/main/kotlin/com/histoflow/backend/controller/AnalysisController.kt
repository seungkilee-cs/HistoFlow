package com.histoflow.backend.controller

import com.histoflow.backend.service.AnalysisService
import org.springframework.core.io.InputStreamResource
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/analysis")
class AnalysisController(
    private val analysisService: AnalysisService
) {
    private val logger = LoggerFactory.getLogger(AnalysisController::class.java)

    @PostMapping("/trigger/{imageId}")
    fun triggerAnalysis(
        @PathVariable imageId: String,
        @RequestParam(required = false) tileLevel: Int?,
        @RequestParam(required = false) threshold: Float?,
        @RequestParam(required = false) tissueThreshold: Float?,
        @RequestParam(required = false) batchSize: Int?
    ): ResponseEntity<*> {
        return try {
            val response = analysisService.triggerAnalysis(
                imageId = imageId,
                tileLevel = tileLevel,
                threshold = threshold,
                tissueThreshold = tissueThreshold,
                batchSize = batchSize
            )
            ResponseEntity.ok(response)
        } catch (e: AnalysisService.AnalysisProxyException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/status/{jobId}")
    fun getStatus(@PathVariable jobId: String): ResponseEntity<*> {
        return try {
            val status = analysisService.getStatus(jobId)
            ResponseEntity.ok(status)
        } catch (e: AnalysisService.AnalysisProxyException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/results/{jobId}")
    fun getResults(@PathVariable jobId: String): ResponseEntity<*> {
        return try {
            val results = analysisService.getResults(jobId)
            ResponseEntity.ok(results)
        } catch (e: AnalysisService.AnalysisProxyException) {
            ResponseEntity.status(e.statusCode).body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/heatmap/{jobId}")
    fun getHeatmap(@PathVariable jobId: String): ResponseEntity<Any> {
        return try {
            val results = analysisService.getResults(jobId)
            val heatmapKey = results.heatmapKey
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "No heatmap key available for job $jobId"))

            val body = InputStreamResource(analysisService.getHeatmapObject(heatmapKey))

            ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body)
        } catch (e: AnalysisService.AnalysisProxyException) {
            val status = if (e.statusCode == 202) HttpStatus.CONFLICT else HttpStatus.valueOf(e.statusCode)
            if (status.is5xxServerError) {
                logger.error("Heatmap retrieval failed for job {}: {}", jobId, e.message)
            }
            ResponseEntity.status(status).body(mapOf("error" to e.message))
        }
    }
}

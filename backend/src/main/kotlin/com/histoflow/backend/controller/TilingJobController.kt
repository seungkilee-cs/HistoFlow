package com.histoflow.backend.controller

import com.histoflow.backend.domain.tiling.TilingJobStage
import com.histoflow.backend.dto.tiling.TilingJobActivityEntry
import com.histoflow.backend.dto.tiling.TilingJobStatusResponse
import com.histoflow.backend.service.TilingJobEventUpdate
import com.histoflow.backend.service.TilingJobService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

data class TilingJobsResponse(
    val jobs: List<TilingJobStatusResponse>
)

data class TilingJobEventRequest(
    val stage: TilingJobStage,
    val message: String? = null,
    val failureReason: String? = null,
    val metadataPath: String? = null,
    val datasetName: String? = null,
    val stageProgressPercent: Int? = null,
    val activityEntries: List<TilingJobActivityEntry> = emptyList()
)

@RestController
@RequestMapping("/api/v1/tiling")
class TilingJobController(
    private val tilingJobService: TilingJobService
) {
    @GetMapping("/jobs/{jobId}")
    fun getJob(@PathVariable jobId: UUID): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(tilingJobService.getJob(jobId))
        } catch (_: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Tiling job not found"))
        }
    }

    @GetMapping("/jobs")
    fun listJobs(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<TilingJobsResponse> {
        return ResponseEntity.ok(TilingJobsResponse(tilingJobService.listJobs(limit)))
    }

    @GetMapping("/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamEvents(): SseEmitter = tilingJobService.registerEmitter()
}

@RestController
@RequestMapping("/api/v1/internal/tiling")
class InternalTilingJobController(
    private val tilingJobService: TilingJobService
) {
    @PostMapping("/jobs/{jobId}/events")
    fun updateJob(
        @PathVariable jobId: UUID,
        @RequestBody request: TilingJobEventRequest
    ): ResponseEntity<Any> {
        return try {
            val updated = tilingJobService.updateJob(
                jobId = jobId,
                update = TilingJobEventUpdate(
                    stage = request.stage,
                    message = request.message,
                    failureReason = request.failureReason,
                    metadataPath = request.metadataPath,
                    datasetName = request.datasetName,
                    stageProgressPercent = request.stageProgressPercent,
                    activityEntries = request.activityEntries
                )
            )
            ResponseEntity.ok(updated)
        } catch (_: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Tiling job not found"))
        }
    }
}

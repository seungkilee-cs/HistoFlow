package com.histoflow.backend.controller

import com.histoflow.backend.domain.tiling.TilingJobStage
import com.histoflow.backend.domain.tiling.TilingJobStatus
import com.histoflow.backend.dto.tiling.TilingJobStatusResponse
import com.histoflow.backend.service.TilingJobEventUpdate
import com.histoflow.backend.service.TilingJobService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [TilingJobController::class, InternalTilingJobController::class])
class TilingJobControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var tilingJobService: TilingJobService

    @Test
    fun `lists recent jobs`() {
        val job = sampleJob()
        given(tilingJobService.listJobs(5)).willReturn(listOf(job))

        mockMvc.perform(get("/api/v1/tiling/jobs").queryParam("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobs[0].id").value(job.id.toString()))
            .andExpect(jsonPath("$.jobs[0].imageId").value(job.imageId))
            .andExpect(jsonPath("$.jobs[0].stage").value(job.stage.name))
    }

    @Test
    fun `returns single job detail`() {
        val job = sampleJob()
        given(tilingJobService.getJob(job.id)).willReturn(job)

        mockMvc.perform(get("/api/v1/tiling/jobs/${job.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(job.id.toString()))
            .andExpect(jsonPath("$.datasetName").value(job.datasetName))
            .andExpect(jsonPath("$.message").value(job.message))
    }

    @Test
    fun `forwards internal event update`() {
        val job = sampleJob(status = TilingJobStatus.COMPLETED, stage = TilingJobStage.COMPLETED, message = "Tiles are ready.")
        val update = TilingJobEventUpdate(
            stage = TilingJobStage.COMPLETED,
            message = "Tiles are ready.",
            failureReason = null,
            metadataPath = "img-1/metadata.json",
            datasetName = "Case A"
        )
        given(tilingJobService.updateJob(job.id, update)).willReturn(job.copy(metadataPath = "img-1/metadata.json"))

        mockMvc.perform(
            post("/api/v1/internal/tiling/jobs/${job.id}/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "stage": "COMPLETED",
                      "message": "Tiles are ready.",
                      "metadataPath": "img-1/metadata.json",
                      "datasetName": "Case A"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stage").value("COMPLETED"))
            .andExpect(jsonPath("$.metadataPath").value("img-1/metadata.json"))

        verify(tilingJobService).updateJob(job.id, update)
    }

    @Test
    fun `streams sse events`() {
        given(tilingJobService.registerEmitter()).willReturn(SseEmitter())

        mockMvc.perform(get("/api/v1/tiling/events/stream"))
            .andExpect(status().isOk)
    }

    private fun sampleJob(
        status: TilingJobStatus = TilingJobStatus.IN_PROGRESS,
        stage: TilingJobStage = TilingJobStage.QUEUED,
        message: String = "Upload complete. Waiting for tiling worker."
    ): TilingJobStatusResponse {
        val timestamp = Instant.parse("2026-03-09T12:00:00Z")
        return TilingJobStatusResponse(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            imageId = "img-1",
            datasetName = "Case A",
            status = status,
            stage = stage,
            message = message,
            failureReason = null,
            metadataPath = null,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}

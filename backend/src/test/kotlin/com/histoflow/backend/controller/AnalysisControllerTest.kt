package com.histoflow.backend.controller

import com.histoflow.backend.service.AnalysisService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream

@WebMvcTest(controllers = [AnalysisController::class])
class AnalysisControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var analysisService: AnalysisService

    @Test
    fun `trigger endpoint forwards optional params`() {
        given(
            analysisService.triggerAnalysis(
                imageId = "img-1",
                tileLevel = 12,
                threshold = 0.6f,
                tissueThreshold = 0.2f,
                batchSize = 8
            )
        ).willReturn(
            AnalysisService.AnalyzeResponse(
                job_id = "job-123",
                status = "accepted",
                message = "ok"
            )
        )

        mockMvc.perform(
            post("/api/v1/analysis/trigger/img-1")
                .queryParam("tileLevel", "12")
                .queryParam("threshold", "0.6")
                .queryParam("tissueThreshold", "0.2")
                .queryParam("batchSize", "8")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.job_id").value("job-123"))

        verify(analysisService).triggerAnalysis(
            imageId = "img-1",
            tileLevel = 12,
            threshold = 0.6f,
            tissueThreshold = 0.2f,
            batchSize = 8
        )
    }

    @Test
    fun `heatmap endpoint streams png`() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        given(analysisService.getHeatmapObjectForJob("job-1"))
            .willReturn(ByteArrayInputStream(pngBytes))

        mockMvc.perform(get("/api/v1/analysis/heatmap/job-1"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))

        verify(analysisService).getHeatmapObjectForJob("job-1")
    }

    @Test
    fun `heatmap endpoint maps upstream 202 to 409`() {
        given(analysisService.getResults("job-2")).willThrow(
            AnalysisService.AnalysisProxyException(202, "still processing")
        )

        mockMvc.perform(get("/api/v1/analysis/heatmap/job-2"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("still processing"))
    }
}

package com.histoflow.backend.controller

import com.histoflow.backend.domain.analysis.AnalysisJobStatus
import com.histoflow.backend.dto.analysis.AnalysisJobResponse
import com.histoflow.backend.dto.analysis.SaveAnalysisRequest
import com.histoflow.backend.dto.analysis.SavedAnalysisResponse
import com.histoflow.backend.service.AnalysisService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
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
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [AnalysisController::class])
@AutoConfigureMockMvc(addFilters = false)
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
        given(analysisService.getHeatmapObjectForJob("job-2")).willThrow(
            AnalysisService.AnalysisProxyException(202, "still processing")
        )

        mockMvc.perform(get("/api/v1/analysis/heatmap/job-2"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("still processing"))
    }

    @Test
    fun `results endpoint can skip tile predictions`() {
        given(analysisService.getResults("job-1", false)).willReturn(
            AnalysisService.AnalysisResultResponse(
                imageId = "img-1",
                tileLevel = 12,
                summary = AnalysisService.AnalysisSummaryResponse(
                    totalTiles = 100,
                    tissueTiles = 80,
                    skippedTiles = 20,
                    flaggedTiles = 12,
                    tumorAreaPercentage = 15.0,
                    aggregateScore = 0.62,
                    maxScore = 0.97,
                    aggregationMethod = "mean",
                    threshold = 0.5
                ),
                heatmapKey = "img-1/heatmap.png",
                summaryKey = "img-1/summary.json",
                resultsKey = "img-1/predictions.json",
                tilePredictionsIncluded = false,
                tilePredictions = emptyList()
            )
        )

        mockMvc.perform(
            get("/api/v1/analysis/results/job-1")
                .queryParam("includeTilePredictions", "false")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.image_id").value("img-1"))
            .andExpect(jsonPath("$.tile_predictions_included").value(false))
            .andExpect(jsonPath("$.tile_predictions.length()").value(0))
            .andExpect(jsonPath("$.summary.tumor_area_percentage").value(15.0))

        verify(analysisService).getResults("job-1", false)
    }

    @Test
    fun `summary endpoint returns lightweight analysis result`() {
        given(analysisService.getResults("job-1", false)).willReturn(
            AnalysisService.AnalysisResultResponse(
                imageId = "img-1",
                tileLevel = 12,
                summary = AnalysisService.AnalysisSummaryResponse(
                    totalTiles = 100,
                    tissueTiles = 80,
                    skippedTiles = 20,
                    flaggedTiles = 12,
                    tumorAreaPercentage = 15.0,
                    aggregateScore = 0.62,
                    maxScore = 0.97,
                    aggregationMethod = "mean",
                    threshold = 0.5
                ),
                heatmapKey = "img-1/heatmap.png",
                summaryKey = "img-1/summary.json",
                resultsKey = "img-1/predictions.json",
                tilePredictionsIncluded = false,
                tilePredictions = emptyList()
            )
        )

        mockMvc.perform(get("/api/v1/analysis/summary/job-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.image_id").value("img-1"))
            .andExpect(jsonPath("$.tile_predictions_included").value(false))
            .andExpect(jsonPath("$.tile_predictions.length()").value(0))
            .andExpect(jsonPath("$.summary.tumor_area_percentage").value(15.0))

        verify(analysisService).getResults("job-1", false)
    }

    @Test
    fun `artifacts endpoint returns object keys without result payloads`() {
        given(analysisService.getArtifacts("job-1")).willReturn(
            AnalysisService.AnalysisArtifactsResponse(
                jobId = "job-1",
                imageId = "img-1",
                status = AnalysisJobStatus.COMPLETED,
                heatmapKey = "img-1/heatmap.png",
                summaryKey = "img-1/summary.json",
                resultsKey = "img-1/predictions.json"
            )
        )

        mockMvc.perform(get("/api/v1/analysis/artifacts/job-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.job_id").value("job-1"))
            .andExpect(jsonPath("$.image_id").value("img-1"))
            .andExpect(jsonPath("$.summary_key").value("img-1/summary.json"))
            .andExpect(jsonPath("$.results_key").value("img-1/predictions.json"))

        verify(analysisService).getArtifacts("job-1")
    }

    @Test
    fun `save endpoint persists completed analysis reference`() {
        val request = SaveAnalysisRequest(
            title = "Baseline run",
            notes = "Initial saved result",
            pinned = true
        )
        val saved = sampleSavedAnalysis(
            title = "Baseline run",
            notes = "Initial saved result",
            pinned = true
        )
        given(analysisService.saveAnalysis("job-1", request)).willReturn(saved)

        mockMvc.perform(
            post("/api/v1/analysis/saved/job-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Baseline run",
                      "notes": "Initial saved result",
                      "pinned": true
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(saved.id.toString()))
            .andExpect(jsonPath("$.jobId").value("job-1"))
            .andExpect(jsonPath("$.imageId").value("img-1"))
            .andExpect(jsonPath("$.title").value("Baseline run"))
            .andExpect(jsonPath("$.pinned").value(true))

        verify(analysisService).saveAnalysis("job-1", request)
    }

    @Test
    fun `saved analysis endpoint loads saved metadata`() {
        val savedId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val saved = sampleSavedAnalysis(id = savedId)
        given(analysisService.getSavedAnalysis(savedId)).willReturn(saved)

        mockMvc.perform(get("/api/v1/analysis/saved/$savedId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(savedId.toString()))
            .andExpect(jsonPath("$.jobId").value("job-1"))
            .andExpect(jsonPath("$.summaryKey").value("img-1/summary.json"))

        verify(analysisService).getSavedAnalysis(savedId)
    }

    @Test
    fun `saved result endpoint loads lightweight result by default`() {
        val savedId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        given(analysisService.getSavedAnalysisResult(savedId, false)).willReturn(
            AnalysisService.AnalysisResultResponse(
                imageId = "img-1",
                tileLevel = 12,
                summary = AnalysisService.AnalysisSummaryResponse(
                    totalTiles = 100,
                    tissueTiles = 80,
                    skippedTiles = 20,
                    flaggedTiles = 12,
                    tumorAreaPercentage = 15.0,
                    aggregateScore = 0.62,
                    maxScore = 0.97,
                    aggregationMethod = "mean",
                    threshold = 0.5
                ),
                heatmapKey = "img-1/heatmap.png",
                summaryKey = "img-1/summary.json",
                resultsKey = "img-1/predictions.json",
                tilePredictionsIncluded = false,
                tilePredictions = emptyList()
            )
        )

        mockMvc.perform(get("/api/v1/analysis/saved/$savedId/results"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.image_id").value("img-1"))
            .andExpect(jsonPath("$.tile_predictions_included").value(false))
            .andExpect(jsonPath("$.tile_predictions.length()").value(0))

        verify(analysisService).getSavedAnalysisResult(savedId, false)
    }

    @Test
    fun `saved analyses endpoint lists saved results for an image`() {
        val saved = sampleSavedAnalysis()
        given(analysisService.listSavedAnalysesForImage("img-1", 5)).willReturn(listOf(saved))

        mockMvc.perform(get("/api/v1/analysis/saved/image/img-1").queryParam("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.analyses[0].id").value(saved.id.toString()))
            .andExpect(jsonPath("$.analyses[0].jobId").value("job-1"))
            .andExpect(jsonPath("$.analyses[0].imageId").value("img-1"))

        verify(analysisService).listSavedAnalysesForImage("img-1", 5)
    }

    @Test
    fun `history endpoint returns completed jobs for image`() {
        val job = AnalysisJobResponse(
            id = UUID.randomUUID(),
            jobId = "job-1",
            imageId = "img-1",
            status = AnalysisJobStatus.COMPLETED,
            tileLevel = 12,
            threshold = 0.5f,
            tissueThreshold = 0.15f,
            tilesProcessed = 400,
            totalTiles = 400,
            tumorAreaPercentage = 18.4,
            aggregateScore = 0.712,
            maxScore = 0.981,
            heatmapKey = "img-1/heatmap_level_12.png",
            summaryKey = "img-1/summary.json",
            resultsKey = "img-1/predictions.json",
            errorMessage = null
        )
        given(analysisService.getHistoryForImage("img-1", 5)).willReturn(listOf(job))

        mockMvc.perform(get("/api/v1/analysis/history/img-1").queryParam("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobs[0].jobId").value("job-1"))
            .andExpect(jsonPath("$.jobs[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.jobs[0].tumorAreaPercentage").value(18.4))
    }

    private fun sampleSavedAnalysis(
        id: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        title: String? = "Baseline run",
        notes: String? = "Initial saved result",
        pinned: Boolean = false
    ): SavedAnalysisResponse {
        val timestamp = Instant.parse("2026-03-09T12:00:00Z")
        return SavedAnalysisResponse(
            id = id,
            jobId = "job-1",
            imageId = "img-1",
            title = title,
            notes = notes,
            pinned = pinned,
            status = AnalysisJobStatus.COMPLETED,
            tileLevel = 12,
            threshold = 0.5f,
            tissueThreshold = 0.15f,
            tumorAreaPercentage = 18.4,
            aggregateScore = 0.712,
            maxScore = 0.981,
            heatmapKey = "img-1/heatmap.png",
            summaryKey = "img-1/summary.json",
            resultsKey = "img-1/predictions.json",
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}

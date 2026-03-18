package com.histoflow.backend.controller

import com.histoflow.backend.config.MinioProperties
import com.histoflow.backend.domain.tiling.TilingJobStage
import com.histoflow.backend.domain.tiling.TilingJobStatus
import com.histoflow.backend.dto.tiling.TilingJobStatusResponse
import com.histoflow.backend.service.TilingJobService
import com.histoflow.backend.service.TilingTriggerService
import com.histoflow.backend.service.UploadService
import com.histoflow.backend.service.InitiateResult
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [MultipartUploadController::class])
class MultipartUploadControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var uploadService: UploadService

    @MockBean
    private lateinit var tilingJobService: TilingJobService

    @MockBean
    private lateinit var tilingTriggerService: TilingTriggerService

    @MockBean
    private lateinit var minioProperties: MinioProperties

    @Test
    fun `initiate returns image id and dataset name`() {
        given(minioProperties.buckets).willReturn(MinioProperties.Buckets(uploads = "unprocessed-slides"))
        given(
            uploadService.initiateMultipartUpload(
                filename = "case.svs",
                contentType = "application/octet-stream",
                partSizeHint = 16_777_216L,
                datasetName = "Case A"
            )
        ).willReturn(
            InitiateResult(
                uploadId = "upload-1",
                key = "img-1/case.svs",
                partSize = 16_777_216L,
                imageId = "img-1",
                datasetName = "Case A"
            )
        )

        mockMvc.perform(
            post("/api/v1/uploads/multipart/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "filename": "case.svs",
                      "contentType": "application/octet-stream",
                      "partSizeHint": 16777216,
                      "datasetName": "Case A"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uploadId").value("upload-1"))
            .andExpect(jsonPath("$.key").value("img-1/case.svs"))
            .andExpect(jsonPath("$.imageId").value("img-1"))
            .andExpect(jsonPath("$.datasetName").value("Case A"))
    }

    @Test
    fun `complete returns job id and triggers tiling`() {
        val jobId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val createdAt = Instant.parse("2026-03-09T12:00:00Z")
        val job = TilingJobStatusResponse(
            id = jobId,
            imageId = "img-1",
            datasetName = "Case A",
            status = TilingJobStatus.IN_PROGRESS,
            stage = TilingJobStage.QUEUED,
            message = "Upload complete. Waiting for tiling worker.",
            createdAt = createdAt,
            updatedAt = createdAt
        )

        given(minioProperties.buckets).willReturn(MinioProperties.Buckets(uploads = "unprocessed-slides"))
        doNothing().`when`(uploadService).completeMultipartUpload(
            "upload-1",
            "img-1/case.svs",
            listOf(Pair(1, "etag-1"))
        )
        given(tilingJobService.createQueuedJob("img-1", "Case A")).willReturn(job)
        doNothing().`when`(tilingTriggerService).triggerTiling(
            jobId = jobId,
            imageId = "img-1",
            sourceBucket = "unprocessed-slides",
            sourceObjectName = "img-1/case.svs",
            datasetName = "Case A"
        )

        mockMvc.perform(
            post("/api/v1/uploads/multipart/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "uploadId": "upload-1",
                      "key": "img-1/case.svs",
                      "imageId": "img-1",
                      "datasetName": "Case A",
                      "parts": [
                        { "partNumber": 1, "etag": "etag-1" }
                      ]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.imageId").value("img-1"))
            .andExpect(jsonPath("$.datasetName").value("Case A"))
            .andExpect(jsonPath("$.status").value("accepted"))

        verify(tilingJobService).createQueuedJob("img-1", "Case A")
        verify(tilingTriggerService).triggerTiling(
            jobId = jobId,
            imageId = "img-1",
            sourceBucket = "unprocessed-slides",
            sourceObjectName = "img-1/case.svs",
            datasetName = "Case A"
        )
    }
}

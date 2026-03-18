package com.histoflow.backend.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.histoflow.backend.domain.tiling.TilingJobEntity
import com.histoflow.backend.domain.tiling.TilingJobStage
import com.histoflow.backend.domain.tiling.TilingJobStatus
import com.histoflow.backend.dto.tiling.TilingJobActivityEntry
import com.histoflow.backend.dto.tiling.TilingJobStatusResponse
import com.histoflow.backend.repository.tiling.TilingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

data class TilingJobEventUpdate(
    val stage: TilingJobStage,
    val message: String? = null,
    val failureReason: String? = null,
    val metadataPath: String? = null,
    val datasetName: String? = null,
    val stageProgressPercent: Int? = null,
    val activityEntries: List<TilingJobActivityEntry> = emptyList()
)

@Service
class TilingJobService(
    private val tilingJobRepository: TilingJobRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val emitters = CopyOnWriteArrayList<SseEmitter>()
    private val activityEntryType = object : TypeReference<List<TilingJobActivityEntry>>() {}

    fun createQueuedJob(imageId: String, datasetName: String?): TilingJobStatusResponse {
        val queuedMessage = defaultMessageForStage(TilingJobStage.QUEUED)
        val entity = tilingJobRepository.findByImageId(imageId).orElseGet {
            TilingJobEntity(
                imageId = imageId,
                datasetName = datasetName,
                status = TilingJobStatus.IN_PROGRESS,
                stage = TilingJobStage.QUEUED,
                message = queuedMessage
            )
        }

        entity.datasetName = datasetName ?: entity.datasetName
        entity.status = TilingJobStatus.IN_PROGRESS
        entity.stage = TilingJobStage.QUEUED
        entity.message = queuedMessage
        entity.failureReason = null
        entity.metadataPath = null
        entity.stageProgressPercent = null
        entity.activityEntriesJson = serializeActivityEntries(
            listOf(
                createActivityEntry(
                    stage = TilingJobStage.QUEUED,
                    message = queuedMessage
                )
            )
        )

        val saved = tilingJobRepository.save(entity).toResponse()
        publish(saved)
        return saved
    }

    fun markTriggerFailed(jobId: UUID, failureReason: String): TilingJobStatusResponse {
        return updateJob(
            jobId = jobId,
            update = TilingJobEventUpdate(
                stage = TilingJobStage.FAILED,
                message = "Unable to start tiling job.",
                failureReason = failureReason
            )
        )
    }

    fun updateJob(jobId: UUID, update: TilingJobEventUpdate): TilingJobStatusResponse {
        val entity = tilingJobRepository.findById(jobId)
            .orElseThrow { NoSuchElementException("Tiling job $jobId not found") }

        entity.datasetName = update.datasetName ?: entity.datasetName
        entity.stage = update.stage
        val resolvedMessage = update.message ?: defaultMessageForStage(update.stage)
        entity.message = resolvedMessage
        entity.metadataPath = update.metadataPath ?: entity.metadataPath
        entity.failureReason = if (update.stage == TilingJobStage.FAILED) {
            update.failureReason
        } else {
            null
        }
        entity.stageProgressPercent = when {
            update.stageProgressPercent != null -> update.stageProgressPercent.coerceIn(0, 100)
            update.stage == TilingJobStage.COMPLETED -> 100
            update.stage != TilingJobStage.UPLOADING -> null
            else -> entity.stageProgressPercent
        }
        entity.status = when (update.stage) {
            TilingJobStage.COMPLETED -> TilingJobStatus.COMPLETED
            TilingJobStage.FAILED -> TilingJobStatus.FAILED
            TilingJobStage.QUEUED,
            TilingJobStage.DOWNLOADING,
            TilingJobStage.TILING,
            TilingJobStage.UPLOADING -> TilingJobStatus.IN_PROGRESS
        }
        entity.activityEntriesJson = serializeActivityEntries(
            appendActivityEntries(
                existing = parseActivityEntries(entity.activityEntriesJson),
                incoming = if (update.activityEntries.isNotEmpty()) {
                    update.activityEntries
                } else {
                    listOf(
                        createActivityEntry(
                            stage = update.stage,
                            message = resolvedMessage,
                            detail = if (update.stage == TilingJobStage.FAILED) update.failureReason else null
                        )
                    )
                }
            )
        )

        val saved = tilingJobRepository.save(entity).toResponse()
        publish(saved)
        return saved
    }

    fun getJob(jobId: UUID): TilingJobStatusResponse {
        return tilingJobRepository.findById(jobId)
            .orElseThrow { NoSuchElementException("Tiling job $jobId not found") }
            .toResponse()
    }

    fun findByImageId(imageId: String): TilingJobStatusResponse? {
        return tilingJobRepository.findByImageId(imageId)
            .map { it.toResponse() }
            .orElse(null)
    }

    fun listJobs(limit: Int): List<TilingJobStatusResponse> {
        return tilingJobRepository
            .findAllByOrderByUpdatedAtDesc(PageRequest.of(0, limit.coerceIn(1, 50)))
            .map { it.toResponse() }
    }

    fun registerEmitter(): SseEmitter {
        val emitter = SseEmitter(0L)
        emitters.add(emitter)

        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout {
            emitters.remove(emitter)
            emitter.complete()
        }
        emitter.onError {
            emitters.remove(emitter)
            emitter.complete()
        }

        try {
            emitter.send(
                SseEmitter.event()
                    .name("connected")
                    .data(mapOf("status" to "connected"))
            )
        } catch (ex: IOException) {
            logger.debug("Failed to send initial SSE event", ex)
            emitters.remove(emitter)
            emitter.completeWithError(ex)
        }

        return emitter
    }

    private fun publish(job: TilingJobStatusResponse) {
        emitters.forEach { emitter ->
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("tiling-job")
                        .data(job)
                )
            } catch (ex: Exception) {
                emitters.remove(emitter)
                emitter.completeWithError(ex)
            }
        }
    }

    private fun defaultMessageForStage(stage: TilingJobStage): String = when (stage) {
        TilingJobStage.QUEUED -> "Upload complete. Waiting for tiling worker."
        TilingJobStage.DOWNLOADING -> "Downloading source image."
        TilingJobStage.TILING -> "Generating Deep Zoom tiles."
        TilingJobStage.UPLOADING -> "Uploading tiles and metadata."
        TilingJobStage.COMPLETED -> "Tiles are ready."
        TilingJobStage.FAILED -> "Tiling failed."
    }

    private fun TilingJobEntity.toResponse(): TilingJobStatusResponse {
        return TilingJobStatusResponse(
            id = id ?: error("Persisted tiling job must have an id"),
            imageId = imageId,
            datasetName = datasetName,
            status = status,
            stage = stage,
            message = message,
            failureReason = failureReason,
            metadataPath = metadataPath,
            stageProgressPercent = stageProgressPercent,
            activityEntries = parseActivityEntries(activityEntriesJson),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun appendActivityEntries(
        existing: List<TilingJobActivityEntry>,
        incoming: List<TilingJobActivityEntry>
    ): List<TilingJobActivityEntry> {
        return (existing + incoming).takeLast(MAX_ACTIVITY_ENTRIES)
    }

    private fun createActivityEntry(
        stage: TilingJobStage,
        message: String,
        detail: String? = null
    ): TilingJobActivityEntry {
        return TilingJobActivityEntry(
            timestamp = java.time.Instant.now(),
            stage = stage.name,
            message = message,
            detail = detail
        )
    }

    private fun parseActivityEntries(serialized: String?): List<TilingJobActivityEntry> {
        if (serialized.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            objectMapper.readValue(serialized, activityEntryType)
        } catch (ex: Exception) {
            logger.warn("Failed to parse stored tiling activity entries", ex)
            emptyList()
        }
    }

    private fun serializeActivityEntries(entries: List<TilingJobActivityEntry>): String {
        return try {
            objectMapper.writeValueAsString(entries.takeLast(MAX_ACTIVITY_ENTRIES))
        } catch (ex: Exception) {
            logger.warn("Failed to serialize tiling activity entries", ex)
            "[]"
        }
    }

    companion object {
        private const val MAX_ACTIVITY_ENTRIES = 50
    }
}

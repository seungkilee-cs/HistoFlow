package com.histoflow.backend.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.histoflow.backend.config.TilingProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

/**
 * Service responsible for triggering the tiling microservice
 */
@Service
class TilingTriggerService(
    private val restTemplate: RestTemplate,
    private val tilingProperties: TilingProperties,
    private val objectMapper: ObjectMapper  // For manual JSON serialization debugging
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun triggerTiling(
        imageId: String,
        sourceBucket: String,
        sourceObjectName: String,
        datasetName: String?
    ) {
        logger.info("Triggering tiling job: imageId={}, bucket={}, object={}", 
            imageId, sourceBucket, sourceObjectName)
        
        when (tilingProperties.strategy.lowercase()) {
            "direct-http" -> triggerViaHttp(imageId, sourceBucket, sourceObjectName, datasetName)
            else -> {
                logger.error("Unsupported tiling strategy: {}", tilingProperties.strategy)
                throw IllegalStateException("Unsupported tiling trigger strategy")
            }
        }
    }

    private fun triggerViaHttp(
        imageId: String,
        sourceBucket: String,
        sourceObjectName: String,
        datasetName: String?
    ) {
        val url = "${tilingProperties.baseUrl}/jobs/tile-image"

        // Create payload
        val payload = TilingJobRequest(
            imageId = imageId,
            sourceBucket = sourceBucket,
            sourceObjectName = sourceObjectName,
            datasetName = datasetName
        )

        // Manually serialize to verify Jackson works
        val payloadJson = try {
            objectMapper.writeValueAsString(payload)
        } catch (e: Exception) {
            logger.error("Failed to serialize payload to JSON", e)
            throw IllegalStateException("JSON serialization failed", e)
        }

        logger.info("→ POST {} | Payload: {}", url, payloadJson)

        // Set headers
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            accept = listOf(MediaType.APPLICATION_JSON)
        }

        // Create request entity
        val requestEntity = HttpEntity(payload, headers)

        try {
            val response: ResponseEntity<String> = restTemplate.postForEntity(
                url,
                requestEntity,
                String::class.java
            )

            if (response.statusCode.is2xxSuccessful) {
                logger.info("✅ Tiling job triggered successfully: imageId={}, status={}", 
                    imageId, response.statusCode)
                logger.debug("Response: {}", response.body)
            } else {
                logger.error("❌ Tiling service rejected request: status={}, body={}", 
                    response.statusCode, response.body)
                throw IllegalStateException("Tiling service error: ${response.statusCode}")
            }

        } catch (ex: RestClientException) {
            logger.error("❌ HTTP call failed: imageId={}, error={}", imageId, ex.message)
            logger.debug("Full exception:", ex)
            throw IllegalStateException("Failed to call tiling service", ex)
        }
    }
}

/**
 * Request payload for tiling job
 * Maps to Python FastAPI TileJobRequest schema
 */
data class TilingJobRequest(
    @JsonProperty("image_id")
    val imageId: String,

    @JsonProperty("source_bucket")
    val sourceBucket: String,

    @JsonProperty("source_object_name")
    val sourceObjectName: String,

    @JsonProperty("dataset_name")
    val datasetName: String? = null
)

package com.histoflow.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Configuration for RestTemplate with proper JSON serialization
 * and request/response logging for debugging
 */
@Configuration
class RestTemplateConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Create ObjectMapper with Kotlin module support
     */
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
        }
    }

    /**
     * Create RestTemplate with:
     * - Jackson JSON message converter
     * - SimpleClientHttpRequestFactory (avoids WebSocket upgrade confusion)
     * - Request/response logging interceptor
     */
    @Bean
    fun restTemplate(restTemplateBuilder: RestTemplateBuilder, objectMapper: ObjectMapper): RestTemplate {
        // Use SimpleClientHttpRequestFactory to avoid protocol issues
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(10000)  // 10 seconds
            setReadTimeout(30000)     // 30 seconds
        }
        
        // Create Jackson message converter
        val jacksonConverter = MappingJackson2HttpMessageConverter(objectMapper)
        
        return RestTemplate(requestFactory).apply {
            messageConverters = listOf(jacksonConverter)
            interceptors = listOf(loggingInterceptor())
        }
    }

    /**
     * Interceptor to log raw HTTP requests and responses
     * Only logs when DEBUG level is enabled
     */
    private fun loggingInterceptor() = ClientHttpRequestInterceptor { request, body, execution ->
        logRequest(request, body)
        val response = execution.execute(request, body)
        logResponse(response)
        response
    }

    private fun logRequest(request: HttpRequest, body: ByteArray) {
        if (logger.isDebugEnabled) {
            logger.debug("═══ Outgoing HTTP Request ═══")
            logger.debug("URI: {}", request.uri)
            logger.debug("Method: {}", request.method)
            logger.debug("Headers: {}", request.headers)
            logger.debug("Body (raw): {}", String(body, StandardCharsets.UTF_8))
            logger.debug("════════════════════════════")
        }
    }

    private fun logResponse(response: ClientHttpResponse) {
        if (logger.isDebugEnabled) {
            logger.debug("═══ Incoming HTTP Response ═══")
            logger.debug("Status: {}", response.statusCode)
            logger.debug("Headers: {}", response.headers)
            logger.debug("═══════════════════════════════")
        }
    }
}

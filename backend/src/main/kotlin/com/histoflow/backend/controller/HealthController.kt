package com.histoflow.backend.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.services.s3.S3Client
import java.time.Instant

@RestController
@RequestMapping("/api/v1/health")
class HealthController(private val s3Client: S3Client) {

    @GetMapping
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "service" to "histoflow-backend",
            "timestamp" to Instant.now().toString()
        )
    }

    @GetMapping("/minio")
    fun minioHealth(): Map<String, Any> {
        return try {
            val buckets = s3Client.listBuckets()
            mapOf(
                "status" to "connected",
                "buckets" to buckets.buckets().map { it.name() },
                "timestamp" to Instant.now().toString()
            )
        } catch (e: Exception) {
            mapOf(
                "status" to "failed",
                "error" to (e.message ?: "Unknown error"),
                "timestamp" to Instant.now().toString()
            )
        }
    }
}

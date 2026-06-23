package com.histoflow.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the shared secret guarding internal service-to-service
 * callbacks (under `/api/v1/internal/`). Bound from the `internal` prefix.
 */
@ConfigurationProperties(prefix = "internal")
data class InternalSecurityProperties(
    /**
     * Token that the tiling and region-detector services must present in the
     * `X-Internal-Token` header. When blank, all internal callbacks are
     * rejected (fail closed).
     */
    val apiToken: String = ""
)

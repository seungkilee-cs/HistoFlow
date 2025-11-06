package com.histoflow.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tiling")
data class TilingProperties(
    var baseUrl: String = "http://localhost:8000",
    var strategy: String = "direct-http"
)

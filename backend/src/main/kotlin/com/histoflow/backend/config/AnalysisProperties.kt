package com.histoflow.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "analysis")
data class AnalysisProperties(
    var baseUrl: String = "http://localhost:8001"
)

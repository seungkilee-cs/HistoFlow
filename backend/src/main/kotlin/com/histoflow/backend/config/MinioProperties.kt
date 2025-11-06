package com.histoflow.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minio")
data class MinioProperties(
    var endpoint: String = "",
    var publicEndpoint: String = "",
    var accessKey: String = "",
    var secretKey: String = "",
    val buckets: Buckets = Buckets()
) {
    data class Buckets(
        var tiles: String = "histoflow-tiles",
        var uploads: String = "unprocessed-slides"
    )
}

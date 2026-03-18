package com.histoflow.backend.dto.tiling

import java.time.Instant

data class TilingJobActivityEntry(
    val timestamp: Instant,
    val stage: String,
    val message: String,
    val detail: String? = null
)

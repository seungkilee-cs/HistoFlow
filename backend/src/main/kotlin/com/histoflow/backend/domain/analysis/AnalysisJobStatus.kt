package com.histoflow.backend.domain.analysis

/**
 * Represents the lifecycle states for an ML analysis job triggered after an upload completes.
 */
enum class AnalysisJobStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}

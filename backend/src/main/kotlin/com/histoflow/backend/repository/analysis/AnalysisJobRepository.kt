package com.histoflow.backend.repository.analysis

import com.histoflow.backend.domain.analysis.AnalysisJobEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface AnalysisJobRepository : JpaRepository<AnalysisJobEntity, UUID> {
    // Look up a specific run by the job_id returned
    fun findByJobId(jobId: String): Optional<AnalysisJobEntity>

    // Look up recent analysis runs for a given dzi slide
    fun findAllByImageIdOrderByCreatedAtDesc(imageId: String, pageable: Pageable): List<AnalysisJobEntity>
}

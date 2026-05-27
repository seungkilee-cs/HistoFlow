package com.histoflow.backend.repository.analysis

import com.histoflow.backend.domain.analysis.SavedAnalysisEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface SavedAnalysisRepository : JpaRepository<SavedAnalysisEntity, UUID> {
    fun findByAnalysisJobJobId(jobId: String): Optional<SavedAnalysisEntity>

    fun findAllByImageIdOrderByPinnedDescUpdatedAtDesc(
        imageId: String,
        pageable: Pageable
    ): List<SavedAnalysisEntity>
}

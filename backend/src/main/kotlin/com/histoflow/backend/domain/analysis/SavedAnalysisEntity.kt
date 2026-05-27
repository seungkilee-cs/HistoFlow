package com.histoflow.backend.domain.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "saved_analyses",
    indexes = [
        Index(name = "idx_saved_analyses_image_updated", columnList = "image_id, updated_at"),
        Index(name = "idx_saved_analyses_pinned_updated", columnList = "pinned, updated_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_saved_analyses_analysis_job", columnNames = ["analysis_job_id"])
    ]
)
class SavedAnalysisEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    var analysisJob: AnalysisJobEntity,

    @Column(name = "image_id", nullable = false)
    var imageId: String,

    @Column(nullable = true, length = 160)
    var title: String? = null,

    @Column(nullable = true, length = 2000)
    var notes: String? = null,

    @Column(nullable = false)
    var pinned: Boolean = false,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}

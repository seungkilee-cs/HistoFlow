package com.histoflow.backend.domain.tiling

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "tiling_jobs",
    indexes = [
        Index(name = "idx_tiling_jobs_status_updated", columnList = "status, updated_at"),
        Index(name = "idx_tiling_jobs_stage", columnList = "stage"),
        Index(name = "idx_tiling_jobs_tenant", columnList = "tenant")
    ]
)
class TilingJobEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val imageId: String,

    @Column(nullable = true)
    var datasetName: String? = null,

    // Owning tenant, captured from the authenticated principal at creation.
    // Nullable for rows created before tenant scoping; treated as the default
    // tenant at runtime. A backfilling migration belongs with Flyway adoption.
    @Column(nullable = true)
    var tenant: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TilingJobStatus = TilingJobStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var stage: TilingJobStage = TilingJobStage.QUEUED,

    @Column(nullable = true, length = 1024)
    var message: String? = null,

    @Column(nullable = true, length = 1024)
    var failureReason: String? = null,

    @Column(nullable = true)
    var metadataPath: String? = null,

    @Column(nullable = true)
    var stageProgressPercent: Int? = null,

    @Lob
    @Column(nullable = false)
    var activityEntriesJson: String = "[]",

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

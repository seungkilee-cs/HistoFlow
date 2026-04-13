package com.histoflow.backend.domain.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "analysis_jobs")
class AnalysisJobEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    // Job ID returned by the region-detector service — unique per analysis run so we can have multiple runs for the same image
    @Column(nullable = false, unique = true)
    val jobId: String,

    // The slide being analyzed
    @Column(nullable = false)
    val imageId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    // Valid values are in AnalysisJobStatus
    var status: AnalysisJobStatus = AnalysisJobStatus.PROCESSING,

    // Which zoom level of the slide was analyzed. A higher number means more tiles, more detail.
    @Column(nullable = true)
    var tileLevel: Int? = null,

    // The cutoff probability for calling a tile "Tumor". Default is 0.5
    @Column(nullable = true)
    var threshold: Float? = null,

    @Column(nullable = true)
    var tissueThreshold: Float? = null,

    // Progress tracking
    @Column(nullable = false)
    var tilesProcessed: Int = 0,

    // How much of a tile needs to be actual tissue before it gets analyzed. Tiles that are mostly blank background get skipped.
    @Column(nullable = false)
    var totalTiles: Int = 0,

    // The key in MinIO where the heatmap PNG lives
    @Column(nullable = true)
    var heatmapKey: String? = null,

    @Column(nullable = true)
    var summaryKey: String? = null,

    // The key to the full tile predictions JSON
    @Column(nullable = true)
    var resultsKey: String? = null,

    // Summary statistics — populated once the job completes
    @Column(nullable = true)
    var tumorAreaPercentage: Double? = null,

    @Column(nullable = true)
    var aggregateScore: Double? = null,

    @Column(nullable = true)
    var maxScore: Double? = null,

    @Column(nullable = true, length = 512)
    var statusMessage: String? = null,

    // Populated if the job fails
    @Column(nullable = true, length = 1024)
    var errorMessage: String? = null,

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

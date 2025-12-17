package com.histoflow.backend.repository.tiling

import com.histoflow.backend.domain.tiling.TilingJobEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface TilingJobRepository : JpaRepository<TilingJobEntity, UUID> {
    fun findByImageId(imageId: String): Optional<TilingJobEntity>
    fun existsByImageId(imageId: String): Boolean
}

package com.histoflow.backend.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.histoflow.backend.config.TenantContext
import com.histoflow.backend.repository.tiling.TilingJobRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class TilingJobServiceSseTest {

    private val service = TilingJobService(mock(TilingJobRepository::class.java), ObjectMapper())

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    private fun authenticateTenant(tenant: String) {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("user")
            .claim("tenant", tenant)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @Test
    fun `emitters are partitioned by tenant`() {
        authenticateTenant("acme")
        service.registerEmitter()

        authenticateTenant("globex")
        service.registerEmitter()
        service.registerEmitter()

        assertEquals(1, service.emittersForTenant("acme").size)
        assertEquals(2, service.emittersForTenant("globex").size)
        assertEquals(0, service.emittersForTenant("intruder").size)
    }

    @Test
    fun `unauthenticated subscriber registers under the default tenant`() {
        service.registerEmitter()
        assertEquals(1, service.emittersForTenant(TenantContext.DEFAULT_TENANT).size)
    }
}

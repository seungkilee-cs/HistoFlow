package com.histoflow.backend.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

class TenantContextTest {

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    private fun jwt(builder: Jwt.Builder.() -> Unit): Jwt =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("user")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .apply(builder)
            .build()

    @Test
    fun `returns default tenant when unauthenticated`() {
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.currentTenant())
    }

    @Test
    fun `reads tenant claim from the jwt`() {
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(jwt { claim("tenant", "acme") })
        assertEquals("acme", TenantContext.currentTenant())
    }

    @Test
    fun `falls back to default when tenant claim is absent`() {
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt { })
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.currentTenant())
    }
}

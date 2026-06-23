package com.histoflow.backend.service

import com.histoflow.backend.config.SecurityProperties
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.spec.SecretKeySpec

class AuthTokenServiceTest {

    private val secret = "test-only-secret-test-only-secret-0123456789"
    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    private val props = SecurityProperties(
        jwtSecret = secret,
        jwtTtlMinutes = 60,
        devUsername = "tester",
        devPassword = "test-password",
        defaultTenant = "test-tenant"
    )
    private val service = AuthTokenService(NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key)), props)
    private val decoder: JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()

    @Test
    fun `valid credentials mint a verifiable token with subject and tenant`() {
        val token = service.authenticate("tester", "test-password")
        assertNotNull(token)
        val jwt = decoder.decode(token!!)
        assertEquals("tester", jwt.subject)
        assertEquals("test-tenant", jwt.getClaimAsString("tenant"))
        assertEquals("histoflow", jwt.getClaimAsString("iss"))
    }

    @Test
    fun `wrong password returns null`() {
        assertNull(service.authenticate("tester", "wrong"))
    }

    @Test
    fun `wrong username returns null`() {
        assertNull(service.authenticate("attacker", "test-password"))
    }

    @Test
    fun `blank configured password disables login`() {
        val svc = AuthTokenService(
            NimbusJwtEncoder(ImmutableSecret<SecurityContext>(key)),
            props.copy(devPassword = "")
        )
        assertNull(svc.authenticate("tester", "test-password"))
    }
}

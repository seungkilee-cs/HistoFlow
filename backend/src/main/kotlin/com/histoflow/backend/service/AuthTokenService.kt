package com.histoflow.backend.service

import com.histoflow.backend.config.SecurityProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Validates dev credentials and mints self-issued HS256 JWTs carrying the
 * subject and a tenant claim. Returns null when authentication fails.
 */
@Service
class AuthTokenService(
    private val jwtEncoder: JwtEncoder,
    private val properties: SecurityProperties
) {

    fun authenticate(username: String, password: String): String? {
        if (properties.devPassword.isBlank()) return null
        val userOk = constantTimeEquals(username, properties.devUsername)
        val passOk = constantTimeEquals(password, properties.devPassword)
        // Non-short-circuit so the check does not leak which field failed.
        if (!(userOk and passOk)) return null
        return mintToken(username)
    }

    private fun mintToken(subject: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("histoflow")
            .issuedAt(now)
            .expiresAt(now.plus(properties.jwtTtlMinutes, ChronoUnit.MINUTES))
            .subject(subject)
            .claim("tenant", properties.defaultTenant)
            .build()
        // Symmetric key: the encoder must be told to sign with HS256 (it would
        // otherwise default to RS256 and fail to select a key).
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(
            a.toByteArray(StandardCharsets.UTF_8),
            b.toByteArray(StandardCharsets.UTF_8)
        )
}

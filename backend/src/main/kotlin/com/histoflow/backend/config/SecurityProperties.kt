package com.histoflow.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Authentication configuration, bound from the `security` prefix.
 *
 * Self-issued HS256 JWTs: the backend both mints (on login) and validates
 * tokens with a single shared secret, so no external identity provider is
 * required. Tokens carry a subject and a tenant claim.
 */
@ConfigurationProperties(prefix = "security")
data class SecurityProperties(
    /** HMAC secret for HS256. Must be at least 32 characters. Required in non-dev. */
    val jwtSecret: String = "",
    /** Token lifetime in minutes. */
    val jwtTtlMinutes: Long = 120,
    /** Dev login username. */
    val devUsername: String = "histoflow",
    /** Dev login password. Blank disables login (fail closed). */
    val devPassword: String = "",
    /** Tenant claim written into issued tokens (scaffold for per-tenant scoping). */
    val defaultTenant: String = "default"
)

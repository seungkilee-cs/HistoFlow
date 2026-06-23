package com.histoflow.backend.config

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Resolves the tenant of the current request from the authenticated JWT's
 * tenant claim. Falls back to the default tenant for unauthenticated or
 * non-JWT contexts (internal service callbacks, tests).
 */
object TenantContext {
    const val DEFAULT_TENANT = "default"
    const val CLAIM = "tenant"

    fun currentTenant(): String {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is Jwt) {
            return principal.getClaimAsString(CLAIM)?.takeIf { it.isNotBlank() } ?: DEFAULT_TENANT
        }
        return DEFAULT_TENANT
    }
}

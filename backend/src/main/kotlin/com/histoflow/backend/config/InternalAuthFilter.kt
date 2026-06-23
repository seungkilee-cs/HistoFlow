package com.histoflow.backend.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Guards the internal service-to-service callback endpoints
 * (under `/api/v1/internal/`) with a shared-secret header. Only the tiling and
 * region-detector services that carry the configured token may report
 * job progress. Fails closed: if no token is configured, every request is
 * rejected rather than left open.
 */
class InternalAuthFilter(private val expectedToken: String) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (expectedToken.isBlank()) {
            log.error(
                "internal.api-token is not configured; rejecting internal callback to {}",
                request.requestURI
            )
            reject(response)
            return
        }

        val provided = request.getHeader(HEADER)
        if (provided == null || !constantTimeEquals(provided, expectedToken)) {
            log.warn("Rejected unauthenticated internal callback to {}", request.requestURI)
            reject(response)
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun reject(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write("""{"error":"unauthorized"}""")
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(
            a.toByteArray(StandardCharsets.UTF_8),
            b.toByteArray(StandardCharsets.UTF_8)
        )

    companion object {
        const val HEADER = "X-Internal-Token"

        /** URL space the filter guards; the servlet wildcard is appended at registration. */
        const val PATH_PREFIX = "/api/v1/internal/"
    }
}

package com.histoflow.backend.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class InternalAuthFilterTest {

    private val token = "secret-token"

    private fun request(): MockHttpServletRequest =
        MockHttpServletRequest("POST", "/api/v1/internal/tiling/jobs/1/events")

    @Test
    fun `passes through when token matches`() {
        val filter = InternalAuthFilter(token)
        val req = request().apply { addHeader(InternalAuthFilter.HEADER, token) }
        val res = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(req, res, chain)

        verify(chain).doFilter(req, res)
        assertEquals(HttpServletResponse.SC_OK, res.status)
    }

    @Test
    fun `rejects when header missing`() {
        val filter = InternalAuthFilter(token)
        val req = request()
        val res = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(req, res, chain)

        verify(chain, never()).doFilter(req, res)
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.status)
    }

    @Test
    fun `rejects when header is wrong`() {
        val filter = InternalAuthFilter(token)
        val req = request().apply { addHeader(InternalAuthFilter.HEADER, "wrong-token") }
        val res = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(req, res, chain)

        verify(chain, never()).doFilter(req, res)
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.status)
    }

    @Test
    fun `fails closed when no token configured`() {
        val filter = InternalAuthFilter("")
        val req = request().apply { addHeader(InternalAuthFilter.HEADER, "anything") }
        val res = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(req, res, chain)

        verify(chain, never()).doFilter(req, res)
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, res.status)
    }
}

package com.histoflow.backend.controller

import com.histoflow.backend.service.AuthTokenService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [AuthController::class])
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var authTokenService: AuthTokenService

    @Test
    fun `login returns a token for valid credentials`() {
        given(authTokenService.authenticate("tester", "secret")).willReturn("jwt-token-value")

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"tester","password":"secret"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("jwt-token-value"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
    }

    @Test
    fun `login rejects invalid credentials with 401`() {
        given(authTokenService.authenticate("tester", "wrong")).willReturn(null)

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"tester","password":"wrong"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("invalid credentials"))
    }
}

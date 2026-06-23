package com.histoflow.backend.controller

import com.histoflow.backend.service.AuthTokenService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String, val tokenType: String = "Bearer")

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authTokenService: AuthTokenService) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        val token = authTokenService.authenticate(request.username, request.password)
            ?: return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "invalid credentials"))
        return ResponseEntity.ok(LoginResponse(token = token))
    }
}

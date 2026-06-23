package com.histoflow.backend.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import javax.crypto.spec.SecretKeySpec

/**
 * Stateless JWT security for the public API.
 *
 * Open: login and health. The internal service-to-service callbacks are left
 * open here because they are guarded by their own shared-secret filter, not
 * the user JWT. Everything else requires a valid bearer token.
 */
@Configuration
class SecurityConfig(private val properties: SecurityProperties) {

    private fun secretKey(): SecretKeySpec {
        require(properties.jwtSecret.length >= 32) {
            "security.jwt-secret must be at least 32 characters for HS256"
        }
        return SecretKeySpec(properties.jwtSecret.toByteArray(), "HmacSHA256")
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/**").permitAll()
                it.requestMatchers("/api/v1/health", "/api/v1/health/**").permitAll()
                it.requestMatchers("/api/v1/internal/**").permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { rs -> rs.jwt { } }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(secretKey()).macAlgorithm(MacAlgorithm.HS256).build()

    @Bean
    fun jwtEncoder(): JwtEncoder =
        NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey()))
}

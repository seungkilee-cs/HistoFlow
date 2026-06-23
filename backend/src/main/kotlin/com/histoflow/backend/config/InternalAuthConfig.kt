package com.histoflow.backend.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Registers [InternalAuthFilter] for the internal callback URL space only, so
 * the public API is untouched while service-to-service endpoints are gated.
 */
@Configuration
class InternalAuthConfig {

    @Bean
    fun internalAuthFilterRegistration(
        properties: InternalSecurityProperties
    ): FilterRegistrationBean<InternalAuthFilter> {
        val registration = FilterRegistrationBean(InternalAuthFilter(properties.apiToken))
        registration.addUrlPatterns(InternalAuthFilter.PATH_PREFIX + "*")
        registration.order = Ordered.HIGHEST_PRECEDENCE
        registration.setName("internalAuthFilter")
        return registration
    }
}

package com.idempotent.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Spring Security configuration.
 * 
 * Currently supports API Key authentication.
 * Can be extended to support: Basic Auth, OAuth, JWT, etc.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers(HttpMethod.GET, "/idempotency/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/idempotency/ping").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                
                // Static resources (documentation)
                .requestMatchers("/", "/index.html", "/*.html", "/*.css", "/*.js").permitAll()
                
                // Protected endpoints
                .requestMatchers(HttpMethod.POST, "/idempotency/check").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\": \"Unauthorized - Invalid or missing API key\"}");
                })
            );

        http.addFilterBefore(apiKeyAuthenticationFilter, BasicAuthenticationFilter.class);

        return http.build();
    }
}

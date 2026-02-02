package com.idempotent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to extract API Key from request headers and authenticate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;

    private static final String API_KEY_HEADER = "api-key";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isEmpty()) {
            ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(apiKey);
            log.debug("Processing API Key authentication for endpoint: {}", request.getRequestURI());

            var authentication = apiKeyAuthenticationProvider.authenticate(token);

            if (authentication != null && authentication.isAuthenticated()) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("API Key authentication successful for endpoint: {}", request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}

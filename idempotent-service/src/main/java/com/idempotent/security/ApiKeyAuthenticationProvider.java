package com.idempotent.security;

import com.idempotent.service.ApiKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Authentication provider for API Key validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyProvider apiKeyProvider;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof ApiKeyAuthenticationToken)) {
            return null;
        }

        ApiKeyAuthenticationToken token = (ApiKeyAuthenticationToken) authentication;
        String apiKey = token.getApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("API Key authentication failed: missing or empty API key");
            return null;
        }

        if (!apiKeyProvider.isValid(apiKey)) {
            log.warn("API Key authentication failed: invalid API key");
            return null;
        }

        log.debug("API Key authentication successful");
        return new ApiKeyAuthenticationToken(apiKey, java.util.List.of());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}

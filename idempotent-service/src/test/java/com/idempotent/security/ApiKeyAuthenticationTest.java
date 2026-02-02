package com.idempotent.security;

import com.idempotent.service.ApiKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationTest {

    @Mock
    private ApiKeyProvider apiKeyProvider;

    @InjectMocks
    private ApiKeyAuthenticationProvider authenticationProvider;

    @Test
    @DisplayName("Should authenticate successfully with valid API key")
    void shouldAuthenticateWithValidApiKey() {
        String validApiKey = "valid-api-key-123";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(validApiKey);
        
        when(apiKeyProvider.isValid(validApiKey)).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(token);

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).isEqualTo(validApiKey);
    }

    @Test
    @DisplayName("Should return null for invalid API key")
    void shouldRejectInvalidApiKey() {
        String invalidApiKey = "invalid-api-key";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(invalidApiKey);
        
        when(apiKeyProvider.isValid(invalidApiKey)).thenReturn(false);

        Authentication result = authenticationProvider.authenticate(token);
        
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for null API key")
    void shouldRejectNullApiKey() {
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(null);

        Authentication result = authenticationProvider.authenticate(token);
        
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null for empty API key")
    void shouldRejectEmptyApiKey() {
        String emptyApiKey = "";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(emptyApiKey);

        Authentication result = authenticationProvider.authenticate(token);
        
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should support ApiKeyAuthenticationToken")
    void shouldSupportApiKeyAuthenticationToken() {
        boolean supports = authenticationProvider.supports(ApiKeyAuthenticationToken.class);
        
        assertThat(supports).isTrue();
    }
}

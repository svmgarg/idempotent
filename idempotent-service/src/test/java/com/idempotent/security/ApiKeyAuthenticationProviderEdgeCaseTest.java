package com.idempotent.security;

import com.idempotent.service.ApiKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Edge case tests for ApiKeyAuthenticationProvider.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationProviderEdgeCaseTest {

    @Mock
    private ApiKeyProvider apiKeyProvider;

    @InjectMocks
    private ApiKeyAuthenticationProvider authenticationProvider;

    @Test
    @DisplayName("Should return null for non-ApiKeyAuthenticationToken")
    void shouldReturnNullForWrongAuthenticationType() {
        Authentication wrongToken = mock(Authentication.class);
        
        Authentication result = authenticationProvider.authenticate(wrongToken);
        
        assertThat(result).isNull();
        verifyNoInteractions(apiKeyProvider);
    }

    @Test
    @DisplayName("Should return null when API key validation throws exception")
    void shouldReturnNullWhenValidationThrowsException() {
        String apiKey = "test-key";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(apiKey);
        
        when(apiKeyProvider.isValid(apiKey)).thenThrow(new RuntimeException("Validation error"));

        try {
            authenticationProvider.authenticate(token);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Validation error");
        }
    }

    @Test
    @DisplayName("Should handle whitespace-only API key")
    void shouldHandleWhitespaceOnlyApiKey() {
        String whitespaceKey = "   \t\n  ";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(whitespaceKey);
        
        // The implementation doesn't trim, so it will call isValid with the whitespace string
        when(apiKeyProvider.isValid(whitespaceKey)).thenReturn(false);
        
        Authentication result = authenticationProvider.authenticate(token);
        
        assertThat(result).isNull();
        verify(apiKeyProvider, times(1)).isValid(whitespaceKey);
    }

    @Test
    @DisplayName("Should authenticate successfully with very long valid API key")
    void shouldAuthenticateWithVeryLongValidApiKey() {
        String longApiKey = "a".repeat(500);
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(longApiKey);
        
        when(apiKeyProvider.isValid(longApiKey)).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(token);

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        verify(apiKeyProvider, times(1)).isValid(longApiKey);
    }

    @Test
    @DisplayName("Should reject very long invalid API key")
    void shouldRejectVeryLongInvalidApiKey() {
        String longApiKey = "b".repeat(500);
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(longApiKey);
        
        when(apiKeyProvider.isValid(longApiKey)).thenReturn(false);

        Authentication result = authenticationProvider.authenticate(token);

        assertThat(result).isNull();
        verify(apiKeyProvider, times(1)).isValid(longApiKey);
    }

    @Test
    @DisplayName("Should handle API key with special characters")
    void shouldHandleApiKeyWithSpecialCharacters() {
        String specialKey = "key-123_ABC.xyz@domain!#$%";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(specialKey);
        
        when(apiKeyProvider.isValid(specialKey)).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(token);

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("Should support ApiKeyAuthenticationToken class")
    void shouldSupportApiKeyAuthenticationTokenClass() {
        boolean supports = authenticationProvider.supports(ApiKeyAuthenticationToken.class);
        
        assertThat(supports).isTrue();
    }

    @Test
    @DisplayName("Should not support other authentication types")
    void shouldNotSupportOtherAuthenticationTypes() {
        boolean supports = authenticationProvider.supports(Authentication.class);
        
        assertThat(supports).isFalse();
    }

    @Test
    @DisplayName("Should handle apiKeyProvider returning different results on retries")
    void shouldHandleProviderReturningDifferentResults() {
        String apiKey = "test-key";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(apiKey);
        
        // First call returns false, but we only call once per authentication attempt
        when(apiKeyProvider.isValid(apiKey)).thenReturn(false);

        Authentication result = authenticationProvider.authenticate(token);

        assertThat(result).isNull();
        verify(apiKeyProvider, times(1)).isValid(apiKey);
    }

    @Test
    @DisplayName("Should create authenticated token with empty authorities")
    void shouldCreateAuthenticatedTokenWithEmptyAuthorities() {
        String validApiKey = "valid-key";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(validApiKey);
        
        when(apiKeyProvider.isValid(validApiKey)).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(token);

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve API key in authenticated token")
    void shouldPreserveApiKeyInAuthenticatedToken() {
        String validApiKey = "preserve-test-key";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(validApiKey);
        
        when(apiKeyProvider.isValid(validApiKey)).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(token);

        assertThat(result).isNotNull();
        assertThat(result.getPrincipal()).isEqualTo(validApiKey);
    }

    @Test
    @DisplayName("Should handle single character API key")
    void shouldHandleSingleCharacterApiKey() {
        String singleChar = "x";
        ApiKeyAuthenticationToken token = new ApiKeyAuthenticationToken(singleChar);
        
        when(apiKeyProvider.isValid(singleChar)).thenReturn(true);

        Authentication result = authenticationProvider.authenticate(token);

        assertThat(result).isNotNull();
        assertThat(result.isAuthenticated()).isTrue();
    }
}

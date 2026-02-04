package com.idempotent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for JsonFileApiKeyProvider.
 */
@ExtendWith(MockitoExtension.class)
class JsonFileApiKeyProviderTest {

    @Mock
    private Resource apiKeyResource;

    private JsonFileApiKeyProvider apiKeyProvider;

    @BeforeEach
    void setUp() {
        apiKeyProvider = new JsonFileApiKeyProvider(new ObjectMapper());
        ReflectionTestUtils.setField(apiKeyProvider, "apiKeyResource", apiKeyResource);
    }

    @Test
    @DisplayName("Should load multiple API keys from new format")
    void shouldLoadMultipleApiKeysFromNewFormat() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": [\"key1\", \"key2\", \"key3\"]\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid("key1")).isTrue();
        assertThat(apiKeyProvider.isValid("key2")).isTrue();
        assertThat(apiKeyProvider.isValid("key3")).isTrue();
        assertThat(apiKeyProvider.isValid("invalidKey")).isFalse();
    }

    @Test
    @DisplayName("Should load single API key from legacy format")
    void shouldLoadSingleApiKeyFromLegacyFormat() throws IOException {
        String jsonContent = "{\n  \"apiKey\": \"legacyKey\"\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid("legacyKey")).isTrue();
        assertThat(apiKeyProvider.isValid("otherKey")).isFalse();
    }

    @Test
    @DisplayName("Should reject null API key")
    void shouldRejectNullApiKey() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": [\"key1\", \"key2\"]\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("Should reject empty string API key")
    void shouldRejectEmptyStringApiKey() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": [\"key1\", \"key2\"]\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid("")).isFalse();
    }

    @Test
    @DisplayName("Should handle empty API keys array")
    void shouldHandleEmptyApiKeysArray() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": []\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid("anyKey")).isFalse();
    }

    @Test
    @DisplayName("Should handle whitespace in API keys")
    void shouldHandleWhitespaceInApiKeys() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": [\"key-with-spaces\", \"key_with_underscores\", \"key.with.dots\"]\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid("key-with-spaces")).isTrue();
        assertThat(apiKeyProvider.isValid("key_with_underscores")).isTrue();
        assertThat(apiKeyProvider.isValid("key.with.dots")).isTrue();
    }

    @Test
    @DisplayName("Should be case-sensitive for API keys")
    void shouldBeCaseSensitiveForApiKeys() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": [\"MyKey\"]\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid("MyKey")).isTrue();
        assertThat(apiKeyProvider.isValid("mykey")).isFalse();
        assertThat(apiKeyProvider.isValid("MYKEY")).isFalse();
    }

    @Test
    @DisplayName("Should handle duplicate API keys in configuration")
    void shouldHandleDuplicateApiKeys() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": [\"key1\", \"key1\", \"key2\"]\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        // HashSet will automatically deduplicate
        assertThat(apiKeyProvider.isValid("key1")).isTrue();
        assertThat(apiKeyProvider.isValid("key2")).isTrue();
    }

    @Test
    @DisplayName("Should handle API keys with special characters")
    void shouldHandleApiKeysWithSpecialCharacters() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": [\"key-123_ABC.xyz@domain!#$%\"]\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid("key-123_ABC.xyz@domain!#$%")).isTrue();
    }

    @Test
    @DisplayName("Should prioritize apiKeys over apiKey field")
    void shouldPrioritizeApiKeysOverApiKeyField() throws IOException {
        String jsonContent = "{\n  \"apiKeys\": [\"newKey\"],\n  \"apiKey\": \"oldKey\"\n}";
        InputStream inputStream = new ByteArrayInputStream(jsonContent.getBytes());
        
        when(apiKeyResource.getInputStream()).thenReturn(inputStream);

        apiKeyProvider.loadApiKey();

        assertThat(apiKeyProvider.isValid("newKey")).isTrue();
        assertThat(apiKeyProvider.isValid("oldKey")).isFalse();
    }
}

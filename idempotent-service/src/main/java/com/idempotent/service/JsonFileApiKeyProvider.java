package com.idempotent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class JsonFileApiKeyProvider implements ApiKeyProvider {

    private final ObjectMapper objectMapper;

    @Value("classpath:apiKey.json")
    private Resource apiKeyResource;

    private String apiKey;

    @PostConstruct
    public void loadApiKey() throws IOException {
        try {
            JsonNode root = objectMapper.readTree(apiKeyResource.getInputStream());
            this.apiKey = root.path("apiKey").asText();
            log.info("API key loaded from resource");
        } catch (IOException e) {
            log.error("Failed to load API key", e);
            throw e;
        }
    }

    @Override
    public boolean isValid(String apiKey) {
        return apiKey != null && apiKey.equals(this.apiKey);
    }
}

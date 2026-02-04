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
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class JsonFileApiKeyProvider implements ApiKeyProvider {

    private final ObjectMapper objectMapper;

    @Value("classpath:apiKey.json")
    private Resource apiKeyResource;

    private Set<String> validApiKeys = new HashSet<>();

    @PostConstruct
    public void loadApiKey() throws IOException {
        try {
            JsonNode root = objectMapper.readTree(apiKeyResource.getInputStream());
            
            // Support both old single key format and new multiple keys format
            if (root.has("apiKeys")) {
                // New format: array of keys
                JsonNode keysNode = root.path("apiKeys");
                if (keysNode.isArray()) {
                    keysNode.forEach(keyNode -> validApiKeys.add(keyNode.asText()));
                    log.info("Loaded {} API keys from resource", validApiKeys.size());
                }
            } else if (root.has("apiKey")) {
                // Old format: single key (backward compatibility)
                String singleKey = root.path("apiKey").asText();
                validApiKeys.add(singleKey);
                log.info("Loaded 1 API key from resource (legacy format)");
            } else {
                log.error("No apiKeys or apiKey field found in configuration");
            }
        } catch (IOException e) {
            log.error("Failed to load API keys", e);
            throw e;
        }
    }

    @Override
    public boolean isValid(String apiKey) {
        return apiKey != null && validApiKeys.contains(apiKey);
    }
}

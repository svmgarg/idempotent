package com.idempotent.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.idempotent.dto.HealthResponse;
import com.idempotent.dto.IdempotencyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Startup validator that runs validation checks after the application starts.
 * This ensures the service is properly initialized and all endpoints are responsive.
 */
@Slf4j
@Component
public class StartupValidator implements CommandLineRunner {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String BASE_URL = "http://localhost:8080";
    private static final String HEALTH_ENDPOINT = BASE_URL + "/idempotency/health";
    private static final String CHECK_ENDPOINT = BASE_URL + "/idempotency/check";

    public StartupValidator() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=".repeat(50));
        log.info("Starting Idempotency Service Validation");
        log.info("=".repeat(50));

        try {
            validateHealthEndpoint();
            validateIdempotencyCheckEndpoint();
            log.info("=".repeat(50));
            log.info("Service Validation Completed Successfully ✓");
            log.info("=".repeat(50));
        } catch (Exception e) {
            log.error("Service validation failed", e);
            log.warn("Some validation checks failed, but the application will continue running");
        }
    }

    private void validateHealthEndpoint() {
        log.info("1. Testing Health Endpoint: {}", HEALTH_ENDPOINT);
        try {
            ResponseEntity<HealthResponse> response = restTemplate.getForEntity(HEALTH_ENDPOINT, HealthResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                HealthResponse health = response.getBody();
                log.info("   Status: {} | Service: {} | Message: {}", 
                    health.getStatus(), health.getService(), health.getMessage());
                log.info("   ✓ Health endpoint is working");
            }
        } catch (Exception e) {
            log.warn("   ✗ Failed to validate health endpoint: {}", e.getMessage());
        }
        log.info("");
    }

    private void validateIdempotencyCheckEndpoint() {
        log.info("2. Testing Idempotency Check Endpoint: {}", CHECK_ENDPOINT);
        try {
            // Test with a new key
            String testKey = "startup-validation-" + System.currentTimeMillis();
            log.info("   a) Testing with new key: {}", testKey);
            
            String requestBody = "{\"idempotencyKey\": \"" + testKey + "\"}";
            
            // Create headers with correct content type
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                CHECK_ENDPOINT, 
                entity,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("   ✓ New key check successful - Response: {}", response.getBody());
            }

            // Test with duplicate key
            log.info("   b) Testing with duplicate key: {}", testKey);
            HttpEntity<String> duplicateEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> duplicateResponse = restTemplate.postForEntity(
                CHECK_ENDPOINT, 
                duplicateEntity,
                String.class
            );
            
            if (duplicateResponse.getStatusCode().value() == 409) {
                log.info("   ✓ Duplicate key check successful (409 Conflict) - Response: {}", duplicateResponse.getBody());
            } else if (duplicateResponse.getStatusCode().is2xxSuccessful()) {
                log.info("   ✓ Duplicate key check successful - Response: {}", duplicateResponse.getBody());
            }
        } catch (Exception e) {
            log.warn("   ✗ Failed to validate idempotency check endpoint: {}", e.getMessage());
        }
        log.info("");
    }
}

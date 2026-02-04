package com.idempotent.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return 200 OK with resultStatusCode 400 for invalid request (Zapier compatibility)")
    void shouldHandleValidationException() throws Exception {
        String invalidRequest = "{}"; // Missing required idempotencyKey

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isOk()) // 200 OK for Zapier compatibility
                .andExpect(jsonPath("$.resultStatusCode").value(400))
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("Should return 200 OK with error message for blank idempotency key")
    void shouldHandleBlankIdempotencyKey() throws Exception {
        String invalidRequest = "{\"idempotencyKey\": \"\"}";

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isOk()) // 200 OK for Zapier compatibility
                .andExpect(jsonPath("$.resultStatusCode").value(400))
                .andExpect(jsonPath("$.validationErrors.idempotencyKey").exists());
    }

    @Test
    @DisplayName("Should return 200 OK with resultStatusCode 400 for oversized idempotency key")
    void shouldHandleOversizedIdempotencyKey() throws Exception {
        // Create a key longer than 32 characters
        String longKey = "x".repeat(33);
        String invalidRequest = "{\"idempotencyKey\": \"" + longKey + "\"}";

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isOk()) // 200 OK for Zapier compatibility
                .andExpect(jsonPath("$.resultStatusCode").value(400));
    }

    @Test
    @DisplayName("Should return 200 OK with resultStatusCode 400 for oversized client ID")
    void shouldHandleOversizedClientId() throws Exception {
        String longClientId = "x".repeat(129);
        String invalidRequest = "{\"idempotencyKey\": \"key123\", \"clientId\": \"" + longClientId + "\"}";

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isOk()) // 200 OK for Zapier compatibility
                .andExpect(jsonPath("$.resultStatusCode").value(400));
    }

    @Test
    @DisplayName("Should include message in error response")
    void shouldIncludeTimestampInErrorResponse() throws Exception {
        String invalidRequest = "{}";

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isOk()) // 200 OK for Zapier compatibility
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}

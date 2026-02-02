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
    @DisplayName("Should return 400 with validation errors for invalid request")
    void shouldHandleValidationException() throws Exception {
        String invalidRequest = "{}"; // Missing required idempotencyKey

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 400 with error message for blank idempotency key")
    void shouldHandleBlankIdempotencyKey() throws Exception {
        String invalidRequest = "{\"idempotencyKey\": \"\"}";

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details.idempotencyKey").exists());
    }

    @Test
    @DisplayName("Should return 400 for oversized idempotency key")
    void shouldHandleOversizedIdempotencyKey() throws Exception {
        // Create a key longer than 256 characters
        String longKey = "x".repeat(257);
        String invalidRequest = "{\"idempotencyKey\": \"" + longKey + "\"}";

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should return 400 for oversized client ID")
    void shouldHandleOversizedClientId() throws Exception {
        String longClientId = "x".repeat(129);
        String invalidRequest = "{\"idempotencyKey\": \"key123\", \"clientId\": \"" + longClientId + "\"}";

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should include timestamp in error response")
    void shouldIncludeTimestampInErrorResponse() throws Exception {
        String invalidRequest = "{}";

        mockMvc.perform(post("/idempotency/check")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}

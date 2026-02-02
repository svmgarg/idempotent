package com.idempotent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import com.idempotent.service.IdempotencyService;
import com.idempotent.service.ApiKeyProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IdempotencyController.class)
@AutoConfigureMockMvc(addFilters = false)
class IdempotencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private ApiKeyProvider apiKeyProvider;

    @Test
    @DisplayName("POST /idempotency/check - should return 200 for new key")
    void shouldReturn200ForNewKey() throws Exception {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("new-key-123")
                .build();

        IdempotencyResponse response = IdempotencyResponse.builder()
                .idempotencyKey("new-key-123")
                .isNew(true)
                .isDuplicate(false)
                .createdAt(Instant.now())
                .message("Key accepted - first occurrence")
                .processingTimeNanos(50000)
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(response);
        when(apiKeyProvider.isValid(any())).thenReturn(true);

        mockMvc.perform(post("/idempotency/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value("new-key-123"))
                .andExpect(jsonPath("$.isNew").value(true))
                .andExpect(jsonPath("$.isDuplicate").value(false));
    }

    @Test
    @DisplayName("POST /idempotency/check - should return 409 for duplicate key")
    void shouldReturn409ForDuplicateKey() throws Exception {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("existing-key-123")
                .build();

        IdempotencyResponse response = IdempotencyResponse.builder()
                .idempotencyKey("existing-key-123")
                .isNew(false)
                .isDuplicate(true)
                .createdAt(Instant.now().minusSeconds(60))
                .message("Duplicate request detected")
                .processingTimeNanos(30000)
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(response);
        when(apiKeyProvider.isValid(any())).thenReturn(true);

        mockMvc.perform(post("/idempotency/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.idempotencyKey").value("existing-key-123"))
                .andExpect(jsonPath("$.isNew").value(false))
                .andExpect(jsonPath("$.isDuplicate").value(true));
    }

    @Test
    @DisplayName("POST /idempotency/check - should return 400 for missing key")
    void shouldReturn400ForMissingKey() throws Exception {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("")
                .build();

        mockMvc.perform(post("/idempotency/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /idempotency/check - should return 400 for null key")
    void shouldReturn400ForNullKey() throws Exception {
        String json = "{}";

        mockMvc.perform(post("/idempotency/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /idempotency/check - should return 401 when api-key missing/invalid")
    void shouldReturn401WhenApiKeyInvalid() throws Exception {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("new-key-123")
                .build();

        when(apiKeyProvider.isValid(any())).thenReturn(false);

        mockMvc.perform(post("/idempotency/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /idempotency/ping - should return 200 pong")
    void shouldReturnPingPong() throws Exception {
        mockMvc.perform(get("/idempotency/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    @DisplayName("GET /idempotency/health - should return 200 OK")
    void shouldReturnHealthOk() throws Exception {
        mockMvc.perform(get("/idempotency/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("idempotency-service"))
                .andExpect(jsonPath("$.message").value("Service is healthy and operational"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /idempotency/health - health response contains required fields")
    void shouldReturnCompleteHealthResponse() throws Exception {
        mockMvc.perform(get("/idempotency/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.service").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}

package com.idempotent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import com.idempotent.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IdempotencyController.class)
class IdempotencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdempotencyService idempotencyService;

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
    @DisplayName("GET /idempotency/health - should return 200 OK with health details")
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

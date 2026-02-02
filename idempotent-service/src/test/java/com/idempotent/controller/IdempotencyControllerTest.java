package com.idempotent.controller;

import com.idempotent.dto.HealthResponse;
import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import com.idempotent.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyControllerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private IdempotencyController controller;

    @Test
    @DisplayName("Should return 200 with isNew=true for new idempotency key")
    void shouldReturn200ForNewKey() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("new-key-123")
                .build();

        IdempotencyResponse mockResponse = IdempotencyResponse.builder()
                .idempotencyKey("new-key-123")
                .isNew(true)
                .isDuplicate(false)
                .createdAt(Instant.now())
                .message("Key accepted - first occurrence")
                .processingTimeNanos(50000)
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(mockResponse);

        ResponseEntity<IdempotencyResponse> response = controller.checkIdempotency(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getIdempotencyKey()).isEqualTo("new-key-123");
        assertThat(response.getBody().isNew()).isTrue();
        assertThat(response.getBody().isDuplicate()).isFalse();
    }

    @Test
    @DisplayName("Should return 200 with isDuplicate=true for duplicate key")
    void shouldReturn200ForDuplicateKey() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("existing-key-123")
                .build();

        IdempotencyResponse mockResponse = IdempotencyResponse.builder()
                .idempotencyKey("existing-key-123")
                .isNew(false)
                .isDuplicate(true)
                .createdAt(Instant.now().minusSeconds(60))
                .message("Duplicate request detected")
                .processingTimeNanos(30000)
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(mockResponse);

        ResponseEntity<IdempotencyResponse> response = controller.checkIdempotency(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getIdempotencyKey()).isEqualTo("existing-key-123");
        assertThat(response.getBody().isNew()).isFalse();
        assertThat(response.getBody().isDuplicate()).isTrue();
    }

    @Test
    @DisplayName("Should return pong for ping endpoint")
    void shouldReturnPingPong() {
        ResponseEntity<String> response = controller.ping();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("pong");
    }

    @Test
    @DisplayName("Should return health status with UP status")
    void shouldReturnHealthStatus() {
        ResponseEntity<HealthResponse> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("UP");
        assertThat(response.getBody().getService()).isEqualTo("idempotency-service");
        assertThat(response.getBody().getMessage()).isEqualTo("Service is healthy and operational");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}

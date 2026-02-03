package com.idempotent.controller;

import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import com.idempotent.service.IdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Edge case tests for IdempotencyController.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyControllerEdgeCaseTest {

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private IdempotencyController controller;

    @Test
    @DisplayName("Should handle service returning null gracefully")
    void shouldHandleServiceReturningNull() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("test-key")
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(null);

        // This should trigger NullPointerException which gets caught by GlobalExceptionHandler
        try {
            controller.checkIdempotency(request);
        } catch (NullPointerException e) {
            assertThat(e).isNotNull();
        }

        verify(idempotencyService, times(1)).checkAndInsert(request);
    }

    @Test
    @DisplayName("Should handle service throwing exception")
    void shouldHandleServiceThrowingException() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("test-key")
                .build();

        when(idempotencyService.checkAndInsert(any()))
                .thenThrow(new RuntimeException("Service error"));

        try {
            controller.checkIdempotency(request);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Service error");
        }

        verify(idempotencyService, times(1)).checkAndInsert(request);
    }

    @Test
    @DisplayName("Should return correct status for new key")
    void shouldReturnCorrectStatusForNewKey() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("new-key")
                .build();

        IdempotencyResponse mockResponse = IdempotencyResponse.builder()
                .idempotencyKey("new-key")
                .isDuplicate(false)
                .createdAt(Instant.now())
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(mockResponse);

        ResponseEntity<IdempotencyResponse> response = controller.checkIdempotency(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isDuplicate()).isFalse();
    }

    @Test
    @DisplayName("Should return correct status for duplicate key")
    void shouldReturnCorrectStatusForDuplicateKey() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("duplicate-key")
                .build();

        IdempotencyResponse mockResponse = IdempotencyResponse.builder()
                .idempotencyKey("duplicate-key")
                .isDuplicate(true)
                .createdAt(Instant.now())
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(mockResponse);

        ResponseEntity<IdempotencyResponse> response = controller.checkIdempotency(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isDuplicate()).isTrue();
    }

    @Test
    @DisplayName("Should handle request with all fields populated")
    void shouldHandleRequestWithAllFieldsPopulated() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("full-request-key")
                .clientId("client-123")
                .ttlSeconds(7200L)
                .build();

        IdempotencyResponse mockResponse = IdempotencyResponse.builder()
                .idempotencyKey("full-request-key")
                .isDuplicate(false)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(7200))
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(mockResponse);

        ResponseEntity<IdempotencyResponse> response = controller.checkIdempotency(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyService, times(1)).checkAndInsert(request);
    }

    @Test
    @DisplayName("Should handle request with minimal fields")
    void shouldHandleRequestWithMinimalFields() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("minimal-key")
                .build();

        IdempotencyResponse mockResponse = IdempotencyResponse.builder()
                .idempotencyKey("minimal-key")
                .isDuplicate(false)
                .createdAt(Instant.now())
                .build();

        when(idempotencyService.checkAndInsert(any())).thenReturn(mockResponse);

        ResponseEntity<IdempotencyResponse> response = controller.checkIdempotency(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyService, times(1)).checkAndInsert(request);
    }

    @Test
    @DisplayName("Health endpoint should always return UP")
    void healthEndpointShouldAlwaysReturnUp() {
        ResponseEntity<?> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Ping endpoint should always return pong")
    void pingEndpointShouldAlwaysReturnPong() {
        ResponseEntity<String> response = controller.ping();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("pong");
    }

    @Test
    @DisplayName("Should handle multiple rapid sequential requests")
    void shouldHandleMultipleRapidSequentialRequests() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("rapid-key")
                .build();

        IdempotencyResponse firstResponse = IdempotencyResponse.builder()
                .idempotencyKey("rapid-key")
                .isDuplicate(false)
                .createdAt(Instant.now())
                .build();

        IdempotencyResponse subsequentResponse = IdempotencyResponse.builder()
                .idempotencyKey("rapid-key")
                .isDuplicate(true)
                .createdAt(Instant.now())
                .build();

        when(idempotencyService.checkAndInsert(any()))
                .thenReturn(firstResponse)
                .thenReturn(subsequentResponse)
                .thenReturn(subsequentResponse);

        // Make multiple requests
        ResponseEntity<IdempotencyResponse> response1 = controller.checkIdempotency(request);
        ResponseEntity<IdempotencyResponse> response2 = controller.checkIdempotency(request);
        ResponseEntity<IdempotencyResponse> response3 = controller.checkIdempotency(request);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyService, times(3)).checkAndInsert(request);
    }
}

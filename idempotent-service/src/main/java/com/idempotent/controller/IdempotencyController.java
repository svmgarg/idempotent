package com.idempotent.controller;

import com.idempotent.dto.HealthResponse;
import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import com.idempotent.service.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/idempotency")
@RequiredArgsConstructor
public class IdempotencyController {

    private final IdempotencyService idempotencyService;

    /**
     * Atomically checks and inserts an idempotency key.
     * Requires API Key authentication via header: X-API-KEY
     *
     * Returns:
     * - 200 OK with isDuplicate=false if the key was newly inserted (proceed with operation)
     * - 200 OK with isDuplicate=true if the key already exists (skip operation)
     * - 401 Unauthorized if API key is invalid or missing
     *
     * Note: Always returns 200 OK for successful checks (even duplicates) to support
     * integrations like Zapier that treat non-2xx status codes as errors.
     *
     * @param request the idempotency check request containing the key
     * @return the idempotency response
     */
    @PostMapping("/check")
    public ResponseEntity<IdempotencyResponse> checkIdempotency(
            @Valid @RequestBody IdempotencyRequest request) {

        log.debug("Checking idempotency for key: {}", request.getIdempotencyKey());

        IdempotencyResponse response = idempotencyService.checkAndInsert(request);

        // Always return 200 OK for Zapier integration compatibility
        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint - No authentication required.
     * Returns service health status and basic metrics.
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .service("idempotency-service")
                .timestamp(Instant.now())
                .message("Service is healthy and operational")
                .build());
    }

    /**
     * Ping endpoint - No authentication required.
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}


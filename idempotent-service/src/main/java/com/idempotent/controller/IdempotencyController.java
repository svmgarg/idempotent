package com.idempotent.controller;

import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import com.idempotent.service.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/idempotency")
@RequiredArgsConstructor
public class IdempotencyController {

    private final IdempotencyService idempotencyService;

    /**
     * Atomically checks and inserts an idempotency key.
     *
     * Returns:
     * - 200 OK with isNew=true if the key was newly inserted (proceed with operation)
     * - 200 OK with isDuplicate=true if the key already exists (skip operation)
     *
     * @param request the idempotency check request containing the key
     * @return the idempotency response
     */
    @PostMapping("/check")
    public ResponseEntity<IdempotencyResponse> checkIdempotency(
            @Valid @RequestBody IdempotencyRequest request) {

        log.debug("Checking idempotency for key: {}", request.getIdempotencyKey());

        IdempotencyResponse response = idempotencyService.checkAndInsert(request);

        // Return 200 for both new and duplicate - let client decide based on response
        // Alternatively, return 409 Conflict for duplicates
        if (response.isDuplicate()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}

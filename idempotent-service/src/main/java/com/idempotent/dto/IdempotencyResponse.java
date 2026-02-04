package com.idempotent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdempotencyResponse {

    private String idempotencyKey;
    @JsonProperty("isDuplicate")
    private boolean isDuplicate;
    private Instant createdAt;
    private Instant expiresAt;
    private long processingTimeNanos;
    
    // For error cases - Zapier compatibility
    private Integer resultStatusCode;  // HTTP status code (200, 400, 500, etc.)
    private String message;            // Error message if any
    private Map<String, String> validationErrors; // Field-specific validation errors
}

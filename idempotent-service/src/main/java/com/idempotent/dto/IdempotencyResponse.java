package com.idempotent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdempotencyResponse {

    private String idempotencyKey;
    @com.fasterxml.jackson.annotation.JsonProperty("isNew")
    private boolean isNew;
    @com.fasterxml.jackson.annotation.JsonProperty("isDuplicate")
    private boolean isDuplicate;
    private Instant createdAt;
    private Instant expiresAt;
    private String message;
    private long processingTimeNanos;
}

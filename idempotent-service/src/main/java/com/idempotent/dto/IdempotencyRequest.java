package com.idempotent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRequest {

    @NotBlank(message = "Idempotency key is required")
    @Size(min = 1, max = 64, message = "Idempotency key must be between 1 and 64 characters")
    private String idempotencyKey;

    @Size(max = 64, message = "Client ID must not exceed 64 characters")
    private String clientId;

    @Max(value = 3600, message = "TTL cannot exceed 3600 seconds (1 hour)")
    @Min(value = 1, message = "TTL must be at least 1 second")
    private Long ttlSeconds;
}

package com.idempotent.dto;

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
    @Size(min = 1, max = 256, message = "Idempotency key must be between 1 and 256 characters")
    private String idempotencyKey;

    @Size(max = 128, message = "Client ID must not exceed 128 characters")
    private String clientId;

    private Long ttlSeconds;
}

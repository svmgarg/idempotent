package com.idempotent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    private String idempotencyKey;
    private String clientId;
    private Instant createdAt;
    private Instant expiresAt;
}

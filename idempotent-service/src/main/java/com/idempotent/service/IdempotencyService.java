package com.idempotent.service;

import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;

public interface IdempotencyService {

    /**
     * Atomically checks if an idempotency key exists.
     * If it doesn't exist, inserts it and returns isDuplicate=false.
     * If it exists, returns isDuplicate=true.
     *
     * @param request the idempotency check request
     * @return the idempotency response
     */
    IdempotencyResponse checkAndInsert(IdempotencyRequest request);
}

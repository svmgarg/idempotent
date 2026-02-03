package com.idempotent.service;

import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import com.idempotent.model.IdempotencyRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of IdempotencyService using ConcurrentHashMap.
 * Uses putIfAbsent for atomic check-and-insert operation.
 * Suitable for single-instance deployments or testing.
 */
@Slf4j
@Service
@EnableScheduling
@ConditionalOnProperty(name = "idempotency.storage", havingValue = "memory", matchIfMissing = true)
public class InMemoryIdempotencyService implements IdempotencyService {

    @Value("${idempotency.default-ttl-seconds:3600}")
    private long defaultTtlSeconds;

    private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

    @Override
    public IdempotencyResponse checkAndInsert(IdempotencyRequest request) {
        long startNanos = System.nanoTime();

        String key = buildKey(request.getIdempotencyKey(), request.getClientId());
        long ttlSeconds = request.getTtlSeconds() != null ? request.getTtlSeconds() : defaultTtlSeconds;

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        IdempotencyRecord newRecord = IdempotencyRecord.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .clientId(request.getClientId())
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        // Atomic check-and-insert using putIfAbsent
        IdempotencyRecord existingRecord = store.putIfAbsent(key, newRecord);

        long processingTimeNanos = System.nanoTime() - startNanos;

        if (existingRecord == null) {
            // Key was newly inserted
            log.debug("New idempotency key inserted: {}", key);
            return IdempotencyResponse.builder()
                    .idempotencyKey(request.getIdempotencyKey())
                    .isDuplicate(false)
                    .createdAt(now)
                    .expiresAt(expiresAt)
                    .processingTimeNanos(processingTimeNanos)
                    .build();
        } else {
            // Check if the existing record has expired
            if (existingRecord.getExpiresAt().isBefore(now)) {
                // Expired record, try to replace it atomically
                if (store.replace(key, existingRecord, newRecord)) {
                    log.debug("Expired idempotency key replaced: {}", key);
                    return IdempotencyResponse.builder()
                            .idempotencyKey(request.getIdempotencyKey())
                            .isDuplicate(false)
                            .createdAt(now)
                            .expiresAt(expiresAt)
                            .processingTimeNanos(System.nanoTime() - startNanos)
                            .build();
                }
                // If replace failed, another thread beat us - treat as duplicate
            }

            // Key already exists (duplicate request)
            log.debug("Duplicate idempotency key detected: {}", key);
            return IdempotencyResponse.builder()
                    .idempotencyKey(request.getIdempotencyKey())
                    .isDuplicate(true)
                    .createdAt(existingRecord.getCreatedAt())
                    .expiresAt(existingRecord.getExpiresAt())
                    .processingTimeNanos(System.nanoTime() - startNanos)
                    .build();
        }
    }

    private String buildKey(String idempotencyKey, String clientId) {
        if (clientId != null && !clientId.isEmpty()) {
            return clientId + ":" + idempotencyKey;
        }
        return idempotencyKey;
    }

    /**
     * Cleanup expired entries every minute to prevent memory leaks.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredEntries() {
        Instant now = Instant.now();
        int removed = 0;

        for (var entry : store.entrySet()) {
            if (entry.getValue().getExpiresAt().isBefore(now)) {
                if (store.remove(entry.getKey(), entry.getValue())) {
                    removed++;
                }
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} expired idempotency entries", removed);
        }
    }

    /**
     * Returns the current size of the store (for monitoring).
     */
    public int getStoreSize() {
        return store.size();
    }
}

package com.idempotent.service;

import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

/**
 * Redis implementation of IdempotencyService.
 * Uses Redis SET NX (set if not exists) for atomic check-and-insert.
 * Suitable for distributed deployments with multiple service instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "idempotency.storage", havingValue = "redis")
public class RedisIdempotencyService implements IdempotencyService {

    private static final long DEFAULT_TTL_SECONDS = 3600; // 1 hour default TTL
    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    // Lua script for atomic check-and-insert with TTL
    private static final String CHECK_AND_INSERT_SCRIPT = """
            local key = KEYS[1]
            local value = ARGV[1]
            local ttl = tonumber(ARGV[2])
            
            local existing = redis.call('GET', key)
            if existing then
                return existing
            else
                redis.call('SET', key, value, 'EX', ttl)
                return nil
            end
            """;

    private final DefaultRedisScript<String> checkAndInsertScript = new DefaultRedisScript<>(
            CHECK_AND_INSERT_SCRIPT, String.class);

    @Override
    public IdempotencyResponse checkAndInsert(IdempotencyRequest request) {
        long startNanos = System.nanoTime();

        String key = buildKey(request.getIdempotencyKey(), request.getClientId());
        long ttlSeconds = request.getTtlSeconds() != null ? request.getTtlSeconds() : DEFAULT_TTL_SECONDS;

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String value = String.valueOf(now.toEpochMilli());

        try {
            // Use setIfAbsent for atomic operation
            Boolean wasSet = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

            long processingTimeNanos = System.nanoTime() - startNanos;

            if (Boolean.TRUE.equals(wasSet)) {
                // Key was newly inserted
                log.debug("New idempotency key inserted in Redis: {}", key);
                return IdempotencyResponse.builder()
                        .idempotencyKey(request.getIdempotencyKey())
                        .isDuplicate(false)
                        .createdAt(now)
                        .expiresAt(expiresAt)
                        .processingTimeNanos(processingTimeNanos)
                        .build();
            } else {
                // Key already exists
                String existingValue = redisTemplate.opsForValue().get(key);
                Instant existingCreatedAt = existingValue != null
                        ? Instant.ofEpochMilli(Long.parseLong(existingValue))
                        : now;

                Long ttlRemaining = redisTemplate.getExpire(key);
                Instant existingExpiresAt = ttlRemaining != null && ttlRemaining > 0
                        ? Instant.now().plusSeconds(ttlRemaining)
                        : null;

                log.debug("Duplicate idempotency key detected in Redis: {}", key);
                return IdempotencyResponse.builder()
                        .idempotencyKey(request.getIdempotencyKey())
                        .isDuplicate(true)
                        .createdAt(existingCreatedAt)
                        .expiresAt(existingExpiresAt)
                        .processingTimeNanos(System.nanoTime() - startNanos)
                        .build();
            }
        } catch (Exception e) {
            log.error("Error checking idempotency key in Redis: {}", key, e);
            // Provide detailed error context
            throw new RuntimeException("Failed to check idempotency key in Redis: " + e.getMessage(), e);
        }
    }

    private String buildKey(String idempotencyKey, String clientId) {
        StringBuilder sb = new StringBuilder(KEY_PREFIX);
        if (clientId != null && !clientId.isEmpty()) {
            sb.append(clientId).append(":");
        }
        sb.append(idempotencyKey);
        return sb.toString();
    }
}

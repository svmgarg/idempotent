package com.idempotent.service;

import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Redis-backed IdempotencyService.
 * These tests require a Redis instance to be running.
 * Can be skipped in CI environments where Redis is not available.
 */
@SpringBootTest
@ActiveProfiles("redis-test")
class RedisIdempotencyServiceTest {

    @Autowired(required = false)
    private RedisIdempotencyService service;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Skip tests if Redis is not available
        if (redisTemplate != null && service != null) {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        }
    }

    @Test
    @DisplayName("Should return isNew=true for new idempotency key in Redis")
    void shouldReturnNewForFirstRequest() {
        if (service == null) {
            System.out.println("Skipping Redis test - Redis not available");
            return;
        }

        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertTrue(response.isNew());
        assertFalse(response.isDuplicate());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getExpiresAt());
    }

    @Test
    @DisplayName("Should return isDuplicate=true for existing key in Redis")
    void shouldReturnDuplicateForSecondRequest() {
        if (service == null) {
            System.out.println("Skipping Redis test - Redis not available");
            return;
        }

        String key = UUID.randomUUID().toString();
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(key)
                .build();

        // First request
        IdempotencyResponse first = service.checkAndInsert(request);
        assertTrue(first.isNew());

        // Second request with same key
        IdempotencyResponse second = service.checkAndInsert(request);
        assertFalse(second.isNew());
        assertTrue(second.isDuplicate());
    }

    @Test
    @DisplayName("Should handle different keys independently in Redis")
    void shouldHandleDifferentKeysIndependently() {
        if (service == null) {
            System.out.println("Skipping Redis test - Redis not available");
            return;
        }

        IdempotencyRequest request1 = IdempotencyRequest.builder()
                .idempotencyKey("redis-key-1-" + UUID.randomUUID())
                .build();
        IdempotencyRequest request2 = IdempotencyRequest.builder()
                .idempotencyKey("redis-key-2-" + UUID.randomUUID())
                .build();

        IdempotencyResponse response1 = service.checkAndInsert(request1);
        IdempotencyResponse response2 = service.checkAndInsert(request2);

        assertTrue(response1.isNew());
        assertTrue(response2.isNew());
    }

    @Test
    @DisplayName("Should namespace keys by clientId in Redis")
    void shouldNamespaceKeysByClientId() {
        if (service == null) {
            System.out.println("Skipping Redis test - Redis not available");
            return;
        }

        String sameKey = "redis-payment-" + UUID.randomUUID();

        IdempotencyRequest request1 = IdempotencyRequest.builder()
                .idempotencyKey(sameKey)
                .clientId("redis-client-A")
                .build();
        IdempotencyRequest request2 = IdempotencyRequest.builder()
                .idempotencyKey(sameKey)
                .clientId("redis-client-B")
                .build();

        IdempotencyResponse response1 = service.checkAndInsert(request1);
        IdempotencyResponse response2 = service.checkAndInsert(request2);

        // Same key but different clients - both should be new
        assertTrue(response1.isNew());
        assertTrue(response2.isNew());
    }

    @Test
    @DisplayName("Should be thread-safe in Redis under concurrent access")
    void shouldBeThreadSafeUnderConcurrentAccess() throws InterruptedException {
        if (service == null) {
            System.out.println("Skipping Redis test - Redis not available");
            return;
        }

        String key = "redis-concurrent-" + UUID.randomUUID();
        int numThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<IdempotencyResponse> responses = Collections.synchronizedList(new ArrayList<>());

        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(key)
                .build();

        // Submit all threads simultaneously
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    responses.add(service.checkAndInsert(request));
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent requests should complete within timeout");
        executor.shutdown();

        // Exactly one should be "new", rest should be "duplicate"
        long newCount = responses.stream().filter(IdempotencyResponse::isNew).count();
        long duplicateCount = responses.stream().filter(IdempotencyResponse::isDuplicate).count();

        assertEquals(1, newCount, "Exactly one request should be marked as new");
        assertEquals(numThreads - 1, duplicateCount, "All other requests should be duplicates");
    }

    @Test
    @DisplayName("Should respect custom TTL in Redis")
    void shouldRespectCustomTtl() {
        if (service == null) {
            System.out.println("Skipping Redis test - Redis not available");
            return;
        }

        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("redis-ttl-" + UUID.randomUUID())
                .ttlSeconds(60L)
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertTrue(response.isNew());
        assertTrue(response.getExpiresAt().isAfter(response.getCreatedAt()));
    }

    @Test
    @DisplayName("Should have low latency in Redis")
    void shouldHaveLowLatency() {
        if (service == null) {
            System.out.println("Skipping Redis test - Redis not available");
            return;
        }

        int iterations = 100;
        long totalNanos = 0;

        for (int i = 0; i < iterations; i++) {
            IdempotencyRequest request = IdempotencyRequest.builder()
                    .idempotencyKey("redis-latency-" + UUID.randomUUID())
                    .build();

            IdempotencyResponse response = service.checkAndInsert(request);
            totalNanos += response.getProcessingTimeNanos();
        }

        double avgMicros = (totalNanos / iterations) / 1000.0;
        System.out.println("Redis average processing time: " + avgMicros + " microseconds");

        // Redis latency should be within a reasonable range (typically 1-5ms)
        assertTrue(avgMicros < 10000, "Average Redis latency should be within 10ms");
    }
}

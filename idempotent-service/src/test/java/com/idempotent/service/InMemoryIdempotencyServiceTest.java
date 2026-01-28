package com.idempotent.service;

import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryIdempotencyServiceTest {

    private InMemoryIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryIdempotencyService();
    }

    @Test
    @DisplayName("Should return isNew=true for new idempotency key")
    void shouldReturnNewForFirstRequest() {
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
    @DisplayName("Should return isDuplicate=true for existing idempotency key")
    void shouldReturnDuplicateForSecondRequest() {
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
    @DisplayName("Should handle different keys independently")
    void shouldHandleDifferentKeysIndependently() {
        IdempotencyRequest request1 = IdempotencyRequest.builder()
                .idempotencyKey("key-1")
                .build();
        IdempotencyRequest request2 = IdempotencyRequest.builder()
                .idempotencyKey("key-2")
                .build();

        IdempotencyResponse response1 = service.checkAndInsert(request1);
        IdempotencyResponse response2 = service.checkAndInsert(request2);

        assertTrue(response1.isNew());
        assertTrue(response2.isNew());
    }

    @Test
    @DisplayName("Should namespace keys by clientId")
    void shouldNamespaceKeysByClientId() {
        String sameKey = "payment-123";

        IdempotencyRequest request1 = IdempotencyRequest.builder()
                .idempotencyKey(sameKey)
                .clientId("client-A")
                .build();
        IdempotencyRequest request2 = IdempotencyRequest.builder()
                .idempotencyKey(sameKey)
                .clientId("client-B")
                .build();

        IdempotencyResponse response1 = service.checkAndInsert(request1);
        IdempotencyResponse response2 = service.checkAndInsert(request2);

        // Same key but different clients - both should be new
        assertTrue(response1.isNew());
        assertTrue(response2.isNew());
    }

    @Test
    @DisplayName("Should be thread-safe under concurrent access")
    void shouldBeThreadSafeUnderConcurrentAccess() throws InterruptedException {
        String key = "concurrent-key";
        int numThreads = 100;
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

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should be "new", rest should be "duplicate"
        long newCount = responses.stream().filter(IdempotencyResponse::isNew).count();
        long duplicateCount = responses.stream().filter(IdempotencyResponse::isDuplicate).count();

        assertEquals(1, newCount, "Exactly one request should be marked as new");
        assertEquals(numThreads - 1, duplicateCount, "All other requests should be duplicates");
    }

    @Test
    @DisplayName("Should respect custom TTL")
    void shouldRespectCustomTtl() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .ttlSeconds(60L)
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertTrue(response.isNew());
        assertTrue(response.getExpiresAt().isAfter(response.getCreatedAt()));
        // Verify TTL is approximately 60 seconds
        long ttlSeconds = response.getExpiresAt().getEpochSecond() - response.getCreatedAt().getEpochSecond();
        assertEquals(60L, ttlSeconds);
    }

    @Test
    @DisplayName("Should have low latency")
    void shouldHaveLowLatency() {
        int iterations = 1000;
        long totalNanos = 0;

        for (int i = 0; i < iterations; i++) {
            IdempotencyRequest request = IdempotencyRequest.builder()
                    .idempotencyKey(UUID.randomUUID().toString())
                    .build();

            IdempotencyResponse response = service.checkAndInsert(request);
            totalNanos += response.getProcessingTimeNanos();
        }

        double avgMicros = (totalNanos / iterations) / 1000.0;
        System.out.println("Average processing time: " + avgMicros + " microseconds");

        // Assert sub-millisecond latency
        assertTrue(avgMicros < 1000, "Average latency should be sub-millisecond");
    }
}

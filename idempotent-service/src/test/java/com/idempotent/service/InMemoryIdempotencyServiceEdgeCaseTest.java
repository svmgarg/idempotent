package com.idempotent.service;

import com.idempotent.dto.IdempotencyRequest;
import com.idempotent.dto.IdempotencyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Additional edge case tests for InMemoryIdempotencyService.
 */
class InMemoryIdempotencyServiceEdgeCaseTest {

    private InMemoryIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryIdempotencyService();
    }

    @Test
    @DisplayName("Should handle expired key and allow reuse")
    void shouldHandleExpiredKeyAndAllowReuse() throws InterruptedException {
        String key = "expiring-key";
        
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(key)
                .ttlSeconds(1L) // 1 second TTL
                .build();

        // First request - should be new
        IdempotencyResponse firstResponse = service.checkAndInsert(request);
        assertThat(firstResponse.isDuplicate()).isFalse();

        // Immediate second request - should be duplicate
        IdempotencyResponse secondResponse = service.checkAndInsert(request);
        assertThat(secondResponse.isDuplicate()).isTrue();

        // Wait for expiration
        Thread.sleep(1100);

        // Third request after expiration - should be new again
        IdempotencyResponse thirdResponse = service.checkAndInsert(request);
        assertThat(thirdResponse.isDuplicate()).isFalse();
    }

    @Test
    @DisplayName("Should handle very long TTL")
    void shouldHandleVeryLongTtl() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("long-ttl-key")
                .ttlSeconds(31536000L) // 1 year
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertThat(response.isDuplicate()).isFalse();
        assertThat(response.getExpiresAt()).isAfter(Instant.now().plusSeconds(31535000));
    }

    @Test
    @DisplayName("Should handle very short TTL")
    void shouldHandleVeryShortTtl() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("short-ttl-key")
                .ttlSeconds(1L)
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertThat(response.isDuplicate()).isFalse();
        assertThat(response.getExpiresAt()).isBefore(Instant.now().plusSeconds(2));
    }

    @Test
    @DisplayName("Should handle null TTL and use default")
    void shouldHandleNullTtlAndUseDefault() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("default-ttl-key")
                .ttlSeconds(null)
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertThat(response.isDuplicate()).isFalse();
        // Default TTL is 1 hour (3600 seconds)
        long ttlSeconds = response.getExpiresAt().getEpochSecond() - response.getCreatedAt().getEpochSecond();
        assertThat(ttlSeconds).isEqualTo(3600L);
    }

    @Test
    @DisplayName("Should handle empty clientId")
    void shouldHandleEmptyClientId() {
        String key = "test-key";
        
        IdempotencyRequest request1 = IdempotencyRequest.builder()
                .idempotencyKey(key)
                .clientId("")
                .build();
        
        IdempotencyRequest request2 = IdempotencyRequest.builder()
                .idempotencyKey(key)
                .clientId(null)
                .build();

        IdempotencyResponse response1 = service.checkAndInsert(request1);
        IdempotencyResponse response2 = service.checkAndInsert(request2);

        // Both should be treated as same key (no clientId namespace)
        assertThat(response1.isDuplicate()).isFalse();
        assertThat(response2.isDuplicate()).isTrue();
    }

    @Test
    @DisplayName("Should handle same key with different clientId as separate")
    void shouldHandleSameKeyWithDifferentClientIdAsSeparate() {
        String sameKey = "shared-key";

        IdempotencyRequest request1 = IdempotencyRequest.builder()
                .idempotencyKey(sameKey)
                .clientId("client-1")
                .build();

        IdempotencyRequest request2 = IdempotencyRequest.builder()
                .idempotencyKey(sameKey)
                .clientId("client-2")
                .build();

        IdempotencyResponse response1 = service.checkAndInsert(request1);
        IdempotencyResponse response2 = service.checkAndInsert(request2);

        // Different clients, so both should be new
        assertThat(response1.isDuplicate()).isFalse();
        assertThat(response2.isDuplicate()).isFalse();
    }

    @Test
    @DisplayName("Should handle same clientId same key as duplicate")
    void shouldHandleSameClientIdSameKeyAsDuplicate() {
        String key = "key";
        String clientId = "client";

        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(key)
                .clientId(clientId)
                .build();

        IdempotencyResponse first = service.checkAndInsert(request);
        IdempotencyResponse second = service.checkAndInsert(request);

        assertThat(first.isDuplicate()).isFalse();
        assertThat(second.isDuplicate()).isTrue();
    }

    @Test
    @DisplayName("Should maintain processing time metrics")
    void shouldMaintainProcessingTimeMetrics() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("metrics-key")
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertThat(response.getProcessingTimeNanos()).isGreaterThan(0L);
        assertThat(response.getProcessingTimeNanos()).isLessThan(1_000_000_000L); // Less than 1 second
    }

    @Test
    @DisplayName("Should cleanup expired entries")
    void shouldCleanupExpiredEntries() throws InterruptedException {
        // Add entries with short TTL
        for (int i = 0; i < 10; i++) {
            IdempotencyRequest request = IdempotencyRequest.builder()
                    .idempotencyKey("cleanup-key-" + i)
                    .ttlSeconds(1L)
                    .build();
            service.checkAndInsert(request);
        }

        int initialSize = service.getStoreSize();
        assertThat(initialSize).isEqualTo(10);

        // Wait for expiration
        Thread.sleep(1100);

        // Trigger cleanup
        service.cleanupExpiredEntries();

        // Verify cleanup
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            int sizeAfterCleanup = service.getStoreSize();
            assertThat(sizeAfterCleanup).isLessThan(initialSize);
        });
    }

    @Test
    @DisplayName("Should handle special characters in idempotency key")
    void shouldHandleSpecialCharactersInKey() {
        String[] specialKeys = {
            "key-with-dash",
            "key_with_underscore",
            "key.with.dot",
            "key:with:colon",
            "key@with@at",
            "key#with#hash",
            "key$with$dollar",
            "key%with%percent"
        };

        for (String key : specialKeys) {
            IdempotencyRequest request = IdempotencyRequest.builder()
                    .idempotencyKey(key)
                    .build();

            IdempotencyResponse response = service.checkAndInsert(request);
            assertThat(response.isDuplicate()).isFalse();
        }
    }

    @Test
    @DisplayName("Should handle very long idempotency key")
    void shouldHandleVeryLongIdempotencyKey() {
        String longKey = "a".repeat(256);
        
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(longKey)
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertThat(response.isDuplicate()).isFalse();
        assertThat(response.getIdempotencyKey()).isEqualTo(longKey);
    }

    @Test
    @DisplayName("Should return consistent timestamps")
    void shouldReturnConsistentTimestamps() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("timestamp-key")
                .build();

        IdempotencyResponse response = service.checkAndInsert(request);

        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.getExpiresAt()).isAfter(response.getCreatedAt());
    }

    @Test
    @DisplayName("Should handle getStoreSize correctly")
    void shouldHandleGetStoreSizeCorrectly() {
        int initialSize = service.getStoreSize();
        
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("size-test-key")
                .build();
        
        service.checkAndInsert(request);
        
        int newSize = service.getStoreSize();
        assertThat(newSize).isEqualTo(initialSize + 1);
    }
}

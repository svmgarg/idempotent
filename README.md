# Idempotency Service

A low-latency idempotency service built with Java Spring Boot that provides atomic check-and-insert operations for idempotency keys.

## Features

- **Atomic Operations**: Uses `ConcurrentHashMap.putIfAbsent()` (in-memory) or Redis `SET NX` for thread-safe atomic check-and-insert
- **Low Latency**: Sub-millisecond response times for in-memory storage
- **TTL Support**: Configurable time-to-live for idempotency keys
- **Client Namespacing**: Optional client ID to namespace keys
- **Dual Storage**: Supports in-memory (default) or Redis storage
- **Auto-cleanup**: Automatic removal of expired entries

## API

### POST /idempotency/check

Atomically checks if an idempotency key exists and inserts it if not.

**Request Body:**
```json
{
  "idempotencyKey": "unique-key-123",
  "clientId": "optional-client-id",
  "ttlSeconds": 3600
}
```

**Response (200 OK - New Key):**
```json
{
  "idempotencyKey": "unique-key-123",
  "isNew": true,
  "isDuplicate": false,
  "createdAt": "2026-01-28T10:30:00Z",
  "expiresAt": "2026-01-28T11:30:00Z",
  "message": "Key accepted - first occurrence",
  "processingTimeNanos": 45000
}
```

**Response (409 Conflict - Duplicate Key):**
```json
{
  "idempotencyKey": "unique-key-123",
  "isNew": false,
  "isDuplicate": true,
  "createdAt": "2026-01-28T10:30:00Z",
  "expiresAt": "2026-01-28T11:30:00Z",
  "message": "Duplicate request detected",
  "processingTimeNanos": 32000
}
```

## Build & Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Build
```bash
./mvnw clean package
```

### Run (In-Memory Storage - Default)
```bash
./mvnw spring-boot:run
```

### Run (Redis Storage)
```bash
IDEMPOTENCY_STORAGE=redis REDIS_HOST=localhost ./mvnw spring-boot:run
```

### Test
```bash
./mvnw test
```

## Usage Example

```bash
# First request - returns 200 with isNew=true
curl -X POST http://localhost:8080/idempotency/check \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "payment-abc-123"}'

# Second request with same key - returns 409 with isDuplicate=true
curl -X POST http://localhost:8080/idempotency/check \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "payment-abc-123"}'
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `idempotency.storage` | `memory` | Storage type: `memory` or `redis` |
| `idempotency.default-ttl-seconds` | `3600` | Default TTL for keys |
| `spring.data.redis.host` | `localhost` | Redis host (when using Redis) |
| `spring.data.redis.port` | `6379` | Redis port (when using Redis) |

## Project Structure

```
idempotent-service/
├── pom.xml
└── src/main/java/com/idempotent/
    ├── IdempotencyServiceApplication.java  # Main application
    ├── controller/
    │   └── IdempotencyController.java      # REST endpoint
    ├── dto/
    │   ├── IdempotencyRequest.java         # Request DTO
    │   └── IdempotencyResponse.java        # Response DTO
    ├── exception/
    │   └── GlobalExceptionHandler.java     # Error handling
    ├── model/
    │   └── IdempotencyRecord.java          # Domain model
    └── service/
        ├── IdempotencyService.java         # Service interface
        ├── InMemoryIdempotencyService.java # In-memory implementation
        └── RedisIdempotencyService.java    # Redis implementation
```

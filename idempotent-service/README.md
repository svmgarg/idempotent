# Idempotency Service

A low-latency, production-grade idempotency service built with Java Spring Boot that provides atomic check-and-insert operations for idempotency keys. This service ensures that duplicate requests are safely detected and handled across distributed systems.

## Features

- **Atomic Operations**: Uses `ConcurrentHashMap.putIfAbsent()` (in-memory) or Redis `SET NX` for thread-safe atomic check-and-insert
- **Low Latency**: Sub-millisecond response times for in-memory storage, typically 1-5ms for Redis
- **TTL Support**: Configurable time-to-live for idempotency keys with automatic expiration
- **Client Namespacing**: Optional client ID to namespace keys for multi-tenant scenarios
- **Dual Storage**: Supports in-memory (default) or Redis storage for single-instance or distributed deployments
- **Auto-cleanup**: Automatic removal of expired entries (scheduled for in-memory, native Redis expiration for Redis)
- **Thread-safe**: Guaranteed atomic operations under high concurrent load (100+ threads tested)
- **Comprehensive Health Checks**: Built-in health endpoint for monitoring
- **Production Ready**: Exception handling, validation, and logging for production environments

## API Endpoints

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

**Parameters:**
- `idempotencyKey` (required): Unique identifier for the request (1-256 characters)
- `clientId` (optional): Client identifier for key namespacing (max 128 characters)
- `ttlSeconds` (optional): Time-to-live for the key in seconds (defaults to 3600)

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

**Response (400 Bad Request - Validation Error):**
```json
{
  "timestamp": "2026-01-28T10:30:00Z",
  "status": 400,
  "error": "Validation Failed",
  "details": {
    "idempotencyKey": "Idempotency key is required"
  }
}
```

### GET /idempotency/health

Health check endpoint that returns the service status.

**Response (200 OK):**
```json
{
  "status": "UP",
  "service": "idempotency-service",
  "timestamp": "2026-01-28T10:30:00Z",
  "message": "Service is healthy and operational"
}
```

### Actuator Endpoints

The service includes Spring Boot Actuator endpoints for monitoring:
- `GET /actuator/health` - Detailed health information
- `GET /actuator/info` - Application information
- `GET /actuator/metrics` - Application metrics

## Architecture

### Storage Backends

#### In-Memory Storage (Default)
- **Use Case**: Single instance deployments, testing, development
- **Pros**: Ultra-low latency (<100 microseconds), no external dependencies
- **Cons**: Not suitable for distributed deployments, data lost on restart
- **Thread Safety**: Uses `ConcurrentHashMap` for atomic operations
- **Cleanup**: Scheduled task runs every 60 seconds to remove expired entries

#### Redis Storage
- **Use Case**: Distributed deployments, production environments
- **Pros**: Distributed across multiple service instances, persistent option available
- **Cons**: Network latency (typically 1-5ms), requires Redis infrastructure
- **Atomic Operations**: Uses Redis `SET NX` for atomic check-and-insert
- **TTL**: Leverages Redis native key expiration

### Concurrency & Performance

- **Atomic Operations**: Both implementations guarantee that exactly one request wins the "first" status
- **Performance**: In-memory typically achieves <100 microseconds per operation
- **Scalability**: Redis backend scales across multiple service instances
- **Thread Safety**: Tested with 100+ concurrent threads

## Build & Run

### Prerequisites
- Java 17+
- Maven 3.8+
- (Optional) Redis 6.0+ for distributed deployments

### Build
```bash
cd idempotent-service
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

### Run with Docker
```bash
# Build the Docker image
docker build -t idempotency-service:latest .

# Run with in-memory storage
docker run -p 8080:8080 idempotency-service:latest

# Run with Redis storage
docker run -e IDEMPOTENCY_STORAGE=redis -e REDIS_HOST=redis-host -p 8080:8080 idempotency-service:latest
```

### Test
```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=InMemoryIdempotencyServiceTest

# Run with coverage
./mvnw test jacoco:report
```

## Usage Examples

### Basic Usage
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

### With Client ID (Multi-tenant)
```bash
# Client A first request
curl -X POST http://localhost:8080/idempotency/check \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "payment-123", "clientId": "client-a"}'

# Client B with same key - treated as different key due to clientId
curl -X POST http://localhost:8080/idempotency/check \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "payment-123", "clientId": "client-b"}'
```

### With Custom TTL
```bash
# Set custom TTL of 1 hour (3600 seconds)
curl -X POST http://localhost:8080/idempotency/check \
  -H "Content-Type: application/json" \
  -d '{"idempotencyKey": "payment-123", "ttlSeconds": 3600}'
```

### Health Check
```bash
curl http://localhost:8080/idempotency/health
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `idempotency.storage` | `memory` | Storage type: `memory` or `redis` |
| `idempotency.default-ttl-seconds` | `3600` | Default TTL for keys in seconds |
| `server.port` | `8080` | HTTP server port |
| `REDIS_HOST` | `localhost` | Redis server host (when using Redis storage) |
| `REDIS_PORT` | `6379` | Redis server port |
| `spring.data.redis.timeout` | `2000ms` | Redis connection timeout |

## Performance Characteristics

### In-Memory Storage
- **Latency**: 50-100 microseconds per operation
- **Throughput**: ~10,000+ operations per second (single thread)
- **Concurrency**: Tested with 100+ concurrent threads
- **Storage**: ~500 bytes per entry

### Redis Storage
- **Latency**: 1-5 milliseconds per operation (typical network)
- **Throughput**: ~1,000+ operations per second (network dependent)
- **Concurrency**: Distributed across unlimited service instances
- **Persistence**: Optional, depends on Redis configuration

## Testing

The service includes comprehensive test coverage:

### Unit Tests
- **InMemoryIdempotencyServiceTest** - Tests for in-memory implementation
  - Basic functionality (new/duplicate detection)
  - Thread safety with 100 concurrent threads
  - TTL and expiration handling
  - Performance benchmarks

- **RedisIdempotencyServiceTest** - Integration tests for Redis implementation
  - Requires Redis instance
  - Tests all functionality against live Redis
  - Concurrent access patterns

- **IdempotencyControllerTest** - REST endpoint tests
  - Valid requests
  - Invalid/missing parameters
  - Health endpoint validation

- **GlobalExceptionHandlerTest** - Exception handling tests
  - Validation errors
  - Field validation
  - Error response format

### Running Tests
```bash
# All tests
./mvnw test

# Only in-memory tests (no Redis required)
./mvnw test -Dtest=InMemoryIdempotencyServiceTest,IdempotencyControllerTest

# Redis tests (requires Redis)
./mvnw test -Dtest=RedisIdempotencyServiceTest
```

## Deployment Recommendations

### Single Instance (In-Memory)
- Use default in-memory storage
- Suitable for low-traffic services or testing
- Data is not persisted across restarts

### Distributed (Redis)
- Set `IDEMPOTENCY_STORAGE=redis`
- Configure Redis with proper replication/clustering
- Monitor Redis memory usage
- Consider Redis persistence options (RDB/AOF)

### High Availability
1. Use Redis with master-replica setup
2. Configure multiple service instances
3. Use load balancer for service distribution
4. Monitor latency and error rates

## Troubleshooting

### High Latency with Redis
- Check Redis server load
- Verify network connectivity
- Consider Redis Cluster for high throughput
- Check `spring.data.redis.timeout` configuration

### Memory Growth (In-Memory)
- Verify TTL is set appropriately
- Check if cleanup task is running (logs)
- Monitor store size with metrics

### Duplicate Key Conflicts
- Ensure idempotency keys are truly unique per operation
- Use client ID for multi-tenant scenarios
- Verify TTL doesn't expire prematurely

## Monitoring & Metrics

The service exposes metrics via Spring Boot Actuator:
```bash
curl http://localhost:8080/actuator/metrics
```

Key metrics to monitor:
- Request count and response times
- Error rates
- Redis connection pool status
- Store size (for in-memory)

## Project Structure

```
src/main/java/com/idempotent/
├── IdempotencyServiceApplication.java  # Main application
├── controller/
│   └── IdempotencyController.java      # REST endpoints
├── dto/
│   ├── HealthResponse.java             # Health check response
│   ├── IdempotencyRequest.java         # Request DTO
│   └── IdempotencyResponse.java        # Response DTO
├── exception/
│   └── GlobalExceptionHandler.java     # Centralized error handling
├── model/
│   └── IdempotencyRecord.java          # Domain model
└── service/
    ├── IdempotencyService.java         # Service interface
    ├── InMemoryIdempotencyService.java # In-memory implementation
    └── RedisIdempotencyService.java    # Redis implementation

src/test/java/com/idempotent/
├── controller/
│   └── IdempotencyControllerTest.java
├── exception/
│   └── GlobalExceptionHandlerTest.java
└── service/
    ├── InMemoryIdempotencyServiceTest.java
    └── RedisIdempotencyServiceTest.java
```

## Code Quality

- **Test Coverage**: Comprehensive unit and integration tests
- **Thread Safety**: Atomic operations tested with concurrent load
- **Input Validation**: Request validation with meaningful error messages
- **Error Handling**: Centralized exception handling with detailed responses
- **Logging**: SLF4J logging at INFO and DEBUG levels
- **Documentation**: JavaDoc comments and inline documentation

## License
MIT

## Contributing
Please submit issues and pull requests to the main repository.

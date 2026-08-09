# Order Service — Project Memory

> Auto-generated from repo analysis. For AI agents working on this codebase.

## Project Overview

- **Name**: order-service
- **Group**: com.igorservice
- **Version**: 1.0.0-SNAPSHOT
- **Description**: Order processing microservice with event-driven architecture
- **Package**: com.igorservice.orderservice
- **Base path**: `/c/Users/igor7/order-service`

## Tech Stack

| Layer         | Technology                          | Version   |
|---------------|-------------------------------------|-----------|
| Language      | Java                                | 21        |
| Framework     | Spring Boot                        | 3.2.5     |
| Build Tool    | Maven                               | 3.9+      |
| Database      | PostgreSQL                          | 16        |
| Migrations    | Flyway                              | (bundled) |
| Messaging     | Apache Kafka (Spring Kafka)        | 3.1.5     |
| Security      | Spring Security + API Key filter   | (bundled) |
| Observability | Micrometer + Prometheus            | (bundled) |
| Logging       | Logstash Logback Encoder           | 7.4       |
| OpenAPI       | SpringDoc OpenAPI                   | 2.5.0     |
| Retry         | Spring Retry                        | (bundled) |
| Validation    | Jakarta Validation                  | (bundled) |
| Testing       | JUnit 5, Mockito, Testcontainers  | 1.19.8    |
| Code Coverage | JaCoCo                              | 0.8.12    |
| Boilerplate   | Lombok                              | (bundled) |
| Container     | Docker (multi-stage)               | -         |
| CI/CD         | GitHub Actions                      | -         |

## Architecture Summary

### Components
- **OrderController** → REST API layer (`/api/v1/orders`)
- **OrderService** → Core business logic, state machine, order number generation
- **OrderSearchService** → Dynamic search via JPA Specifications
- **KafkaEventPublisher** → Fire-and-forget event publishing with retry + DLQ
- **KafkaDlqListener** → Dead letter queue consumer for failed events
- **OrderMetrics** → Micrometer counters, timers, gauges
- **ApiKeyAuthFilter** → X-API-KEY header authentication
- **GlobalExceptionHandler** → @RestControllerAdvice for all error handling
- **KafkaHealthIndicator** → Actuator health check for Kafka connectivity

### Data Flow
1. Client sends POST `/api/v1/orders` with X-API-KEY header
2. ApiKeyAuthFilter validates API key
3. OrderController receives request, delegates to OrderService
4. OrderService creates Order entity, generates order number (ORD-{yyyyMMdd}-{seq})
5. OrderRepository persists to PostgreSQL via JPA
6. KafkaEventPublisher publishes ORDER_CREATED event (fire-and-forget)
7. If Kafka fails, retries 3x with exponential backoff → DLQ fallback
8. OrderResponse returned to client

### Layers
```
Controller → Service → Repository → PostgreSQL
                    ↘ KafkaEventPublisher → Kafka → Consumers
                    ↘ OrderMetrics → Prometheus
```

## Environment Requirements

- Java 21+ (JDK for build, JRE for runtime)
- Maven 3.9+
- Docker + Docker Compose (for local infrastructure)
- PostgreSQL 16
- Kafka (Confluent 7.6.0 images)
- Zookeeper (for Kafka)

## Build / Test / Run / Deploy Commands

### Build
```bash
mvn clean compile                    # Compile only
mvn clean package -DskipTests        # Package (skip tests)
mvn clean package                     # Full build with tests
```

### Test
```bash
mvn test                             # Unit tests only
mvn verify                           # Unit + integration tests
mvn verify -Dspring.profiles.active=test  # With test profile
```

### Run Locally
```bash
docker-compose up -d                 # Start PostgreSQL + Kafka + Zookeeper
mvn spring-boot:run                  # Run the app (dev profile)
```

### Run with Docker
```bash
docker build -t order-service:latest .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/orders \
  -e APP_API_KEY=my-secret-key \
  order-service:latest
```

### Docker Compose (full stack)
```bash
docker-compose up -d                 # Starts postgres, zookeeper, kafka, order-service
```

## API Endpoints

All endpoints require `X-API-KEY` header (except actuator/swagger).

| Method | Path                               | Description                    | Status Codes |
|--------|-------------------------------------|--------------------------------|--------------|
| POST   | `/api/v1/orders`                   | Create a new order             | 201, 400, 422 |
| GET    | `/api/v1/orders`                   | List all orders (paginated)    | 200          |
| GET    | `/api/v1/orders/{orderId}`         | Get order by ID (UUID)         | 200, 404     |
| PUT    | `/api/v1/orders/{orderId}/status`  | Update order status            | 200, 404, 409 |
| DELETE | `/api/v1/orders/{orderId}`         | Cancel an order (soft delete)  | 204, 404, 409 |
| GET    | `/api/v1/orders/search`            | Search with filters            | 200          |

### Pagination Parameters
- `page` (default: 0), `size` (default: 20, max: 100), `sort` (default: createdAt), `direction` (default: desc)

### Search Parameters (GET /search)
- `startDate` (ISO date-time), `endDate` (ISO date-time), `status` (enum), `customerId` (string)

### Unauthenticated Endpoints
- `/actuator/**` — health, info, metrics, prometheus
- `/swagger-ui/**`, `/v3/api-docs/**` — OpenAPI docs

## Key Design Decisions

- **Fire-and-forget events**: Kafka publish failures do NOT fail API calls
- **Order number format**: `ORD-{yyyyMMdd}-{5-digit-sequence}` (AtomicLong, resets daily)
- **Status state machine**: PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED; CANCELLED from any non-terminal state
- **Soft delete**: Orders never physically deleted; DELETE endpoint sets status → CANCELLED
- **Optimistic locking**: `@Version` Long field on Order entity for concurrent update protection
- **UUID primary keys**: All entities use `java.util.UUID` with `GenerationType.UUID`
- **Timestamps**: `java.time.Instant` with `@CreationTimestamp` / `@UpdateTimestamp`

## Coding Conventions

- **Lombok**: `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` for boilerplate
- **DTOs**: `OrderRequest` / `OrderResponse` are Lombok @Data classes (not records, though records used for `StatusUpdateRequest`)
- **Streams**: Used for collection transformations (e.g., `OrderResponse.fromEntity()`)
- **Exception handling**: All controllers go through `GlobalExceptionHandler` (@RestControllerAdvice)
- **Logging**: `@Slf4j`; include context (orderId, customerId) in log messages
- **API versioning**: All endpoints under `/api/v1/`
- **Date/Time**: `java.time.Instant` for timestamps (UTC)
- **IDs**: `java.util.UUID` for primary keys
- **Testing**: `@DisplayName` for readable test names, Given-When-Then structure
- **Test data**: `OrderTestDataBuilder` with fluent API for test fixtures
- **MDC tracing**: `traceId` and `spanId` set in OrderService methods, cleared in finally blocks

## Project Status (per PLAN.md)

### Completed
- [x] Phase 1: Foundation (project structure, Docker Compose, Flyway, JPA, Security)
- [x] Phase 2: Core Business Logic (OrderService, DTOs, Controller, ExceptionHandler, order number generation)
- [x] Phase 3: Event-Driven Architecture (KafkaConfig, KafkaEventPublisher, retry, DLQ)
- [x] Phase 4: Observability (Micrometer, Actuator, structured logging, MDC tracing, OrderMetrics)
- [x] Phase 5: Testing (unit tests, MockMvc, Testcontainers, integration tests, TestDataBuilder)
- [x] Phase 6 (partial): Multi-stage Dockerfile, GitHub Actions CI/CD

### Pending (from PLAN.md)
- [ ] Phase 3: Event schemas (JSON Schema / Avro)
- [ ] Phase 6: JaCoCo code coverage enforcement
- [ ] Phase 6: OWASP security scanning
- [ ] Phase 6: Kubernetes manifests (Helm)
- [ ] Phase 7: Redis caching for hot reads
- [ ] Phase 7: Bulk operations
- [ ] Phase 7: Webhook notifications

## Dependencies

### Internal
None — standalone microservice.

### External Infrastructure
- **PostgreSQL 16** — orders database (`orders` schema)
- **Apache Kafka** — event streaming (Confluent 7.6.0 images)
- **Zookeeper** — Kafka coordination (Confluent 7.6.0)

### Kafka Topics
- `order.created` — published on order creation
- `order.status-changed` — published on status transition
- `order.cancelled` — published on order cancellation
- `order.dlq` — dead letter queue for failed events

## Git Workflow

- **Branch naming**: `feat/<name>`, `fix/<name>`, `chore/<name>`
- **Conventional commits**: `feat(scope):`, `fix(scope):`, `test:`, `docs:`, `chore:`
- **PR process**: Feature branches → PR → merge to main
- **Main branch**: `main` (protected, requires PR)
- **No direct pushes to main** — always create a feature branch

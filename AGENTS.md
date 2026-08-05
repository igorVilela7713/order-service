# AGENTS.md — AI Agent Instructions

This file provides guidance for AI agents working on the Order Service codebase.

## Project Overview

- **Language**: Java 21
- **Framework**: Spring Boot 3.2.5
- **Build Tool**: Maven 3.9+
- **Database**: PostgreSQL 16 with Flyway migrations
- **Messaging**: Apache Kafka (Spring Kafka 3.1.5)
- **Testing**: JUnit 5, Mockito, Testcontainers, MockMvc
- **Security**: API key authentication (X-API-KEY header)
- **Observability**: Micrometer + Prometheus, structured logging (Logstash)

## Directory Structure

```
order-service/
├── src/main/java/com/igorservice/orderservice/
│   ├── OrderServiceApplication.java
│   ├── config/
│   │   ├── ApiKeyAuthFilter.java        # X-API-KEY header validation
│   │   ├── KafkaConfig.java             # Kafka producer configuration
│   │   ├── OpenApiConfig.java           # SpringDoc OpenAPI/Swagger config
│   │   ├── RetryConfig.java             # Spring Retry configuration
│   │   └── SecurityConfig.java          # Spring Security filter chain
│   ├── controller/
│   │   └── OrderController.java         # REST endpoints (CRUD + search)
│   ├── dto/
│   │   ├── OrderRequest.java            # Create order request DTO
│   │   └── OrderResponse.java           # Order response DTO with fromEntity()
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice for all exceptions
│   │   └── OrderNotFoundException.java  # Custom 404 exception
│   ├── health/
│   │   └── KafkaHealthIndicator.java    # Actuator health check for Kafka
│   ├── metrics/
│   │   └── OrderMetrics.java            # Micrometer counters, timers, gauges
│   ├── model/
│   │   ├── Order.java                   # JPA entity with lifecycle methods
│   │   ├── OrderItem.java               # JPA entity (OneToMany from Order)
│   │   └── OrderStatus.java             # Enum with canTransitionTo() state machine
│   ├── repository/
│   │   └── OrderRepository.java         # Spring Data JPA + JpaSpecificationExecutor
│   └── service/
│       ├── KafkaDlqListener.java        # Dead letter queue consumer
│       ├── KafkaEventPublisher.java     # @Retryable event publishing with DLQ fallback
│       ├── OrderSearchService.java      # Specification-based dynamic search
│       └── OrderService.java            # Core business logic + MDC tracing
├── src/main/resources/
│   ├── application.yml                  # Profiles: dev, test, prod
│   ├── logback-spring.xml               # Per-profile logging (console/JSON)
│   └── db/migration/
│       └── V1__create_orders_table.sql  # Flyway migration
├── src/test/java/com/igorservice/orderservice/
│   ├── builder/
│   │   └── OrderTestDataBuilder.java    # Fluent test data builder
│   ├── config/
│   │   └── ApiKeyAuthFilterTest.java    # Security filter tests
│   ├── controller/
│   │   ├── OrderControllerTest.java     # MockMvc integration tests
│   │   └── OrderSearchControllerTest.java
│   ├── exception/
│   │   └── GlobalExceptionHandlerTest.java
│   ├── integration/
│   │   ├── OrderRepositoryIntegrationTest.java
│   │   └── OrderSearchIntegrationTest.java
│   ├── metrics/
│   │   └── OrderMetricsTest.java
│   ├── model/
│   │   ├── OrderItemTest.java
│   │   ├── OrderStatusTest.java         # State machine transition tests
│   │   └── OrderTest.java
│   └── service/
│       ├── KafkaDlqListenerTest.java
│       ├── KafkaEventPublisherTest.java
│       └── OrderServiceTest.java
├── docs/
│   ├── api.md                           # Complete API reference
│   ├── architecture.md                  # System design & data flow
│   └── development.md                   # Developer setup & conventions
├── .github/workflows/ci.yml
├── Dockerfile                           # Multi-stage: Maven builder + JRE
├── docker-compose.yml                   # PostgreSQL + Kafka + Zookeeper
├── pom.xml
├── README.md
├── SPEC.md
├── AGENTS.md
└── PLAN.md
```

## Coding Conventions

1. **Lombok**: Use `@Data`, `@Builder`, `@Slf4j` for boilerplate reduction.
2. **Records**: Use Java records for DTOs (immutable).
3. **Streams**: Use streams for collection transformations.
4. **Exception handling**: All controllers go through `GlobalExceptionHandler`.
5. **Logging**: Use `@Slf4j`; include context (orderId, customerId).
6. **API versioning**: All endpoints under `/api/v1/`.
7. **Date/Time**: Use `java.time.Instant` for timestamps (UTC).
8. **IDs**: Use `java.util.UUID` for primary keys.
9. **Testing**: Use `@DisplayName` for readable test names, Given-When-Then structure.

## Key Design Decisions

- **Events are fire-and-forget**: Kafka publish failures do NOT fail API calls.
- **Order number format**: `ORD-{yyyyMMdd}-{sequence}`.
- **Status transitions are validated**: OrderService enforces valid state machine.
- **Soft delete**: Orders are never physically deleted; status → CANCELLED.
- **Optimistic locking**: Order entity uses `@Version` for concurrent update protection.

## Test Patterns

### TestDataBuilder Usage
```java
Order order = OrderTestDataBuilder.anOrder()
    .withCustomerId("CUST-001")
    .withStatus(OrderStatus.CONFIRMED)
    .build();
```

### Test Structure (Given-When-Then)
```java
@Test
@DisplayName("Should create order with valid request")
void shouldCreateOrder() {
    // Given
    OrderRequest request = OrderTestDataBuilder.anOrderRequest().build();

    // When
    OrderResponse response = orderService.createOrder(request);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo("PENDING");
}
```

### Test Types
- **Unit tests**: `*Test.java` — mock dependencies with Mockito
- **Integration tests**: `*IntegrationTest.java` — use Testcontainers (PostgreSQL, Kafka)
- **Controller tests**: `*ControllerTest.java` — MockMvc with security filters

## Observability Patterns

### OrderMetrics (Micrometer)
- `orders.created.total` — Counter of all orders created
- `order.creation.duration` — Timer with p50/p95/p99 percentiles
- `orders.active.count` — Gauge of non-terminal orders
- `orders.status.changed.total` — Counter tagged by status

### Structured Logging (logback-spring.xml)
- **dev/test**: Colored console output with MDC traceId/spanId
- **prod**: JSON output via LogstashEncoder for log aggregation

### MDC Tracing
- `traceId` — UUID per request for distributed tracing
- `spanId` — Short ID for correlation within a trace
- Set in `OrderService.createOrder()` and `updateOrderStatus()`

## Kafka Patterns

### Event Publishing (KafkaEventPublisher)
- Topics: `order.created`, `order.status-changed`, `order.cancelled`
- `@Retryable` with 3 attempts, exponential backoff (1s → 10s)
- On exhaustion: `@Recover` sends to DLQ topic (`order.dlq`)
- `publishSync()` blocks with `CompletableFuture.join()` to allow retry

### Dead Letter Queue (KafkaDlqListener)
- Listens on `order.dlq` topic
- Logs event details for manual investigation
- TODO: Alerting / metrics integration

### Kafka Config
- Producer: `acks=all`, idempotent, 3 retries
- Serializer: `StringSerializer` (key) + `JsonSerializer` (value)

## Search Patterns

### OrderSearchService
- Uses JPA `Specification<Order>` for dynamic query building
- Supports filters: `startDate`, `endDate`, `status`, `customerId`
- Combines predicates with `CriteriaBuilder.and()`
- Returns `Page<OrderResponse>` with sorting

### OrderRepository
- Extends `JpaRepository<Order, UUID>` + `JpaSpecificationExecutor<Order>`
- Supports both standard CRUD and Specification-based queries

## Common Tasks

### Adding a new endpoint
1. Add DTO(s) in `dto/` if needed
2. Add method in `OrderService` (or new service class)
3. Add route in `OrderController` with `@Operation` annotation
4. Add validation in `GlobalExceptionHandler` if needed
5. Add unit test in `*ServiceTest.java`
6. Add MockMvc test in `*ControllerTest.java`
7. Update `docs/api.md` with endpoint documentation

### Adding a new Kafka event
1. Define event type constant in `KafkaEventPublisher`
2. Add `@Retryable` publish method in `KafkaEventPublisher`
3. Add `@Recover` method for DLQ fallback
4. Call publisher from `OrderService` at appropriate state transition
5. Add unit test in `KafkaEventPublisherTest.java`

### Database changes
1. Create Flyway migration `V{N}__description.sql` in `db/migration/`
2. Update entity model if needed
3. Update repository queries if needed
4. Update `docs/architecture.md` schema section

### Adding new metrics
1. Add meter registration in `OrderMetrics` constructor
2. Add recording method(s)
3. Call from service layer
4. Add test in `OrderMetricsTest.java`

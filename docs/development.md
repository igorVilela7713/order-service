# Development Guide

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21+ | Runtime |
| Maven | 3.9+ | Build tool |
| Docker | 24+ | Container runtime |
| Docker Compose | 2.20+ | Local infrastructure |
| IDE | IntelliJ / VS Code | Development |

## Local Development Setup

### 1. Clone the repository

```bash
git clone https://github.com/igorVilela7713/order-service.git
cd order-service
```

### 2. Start infrastructure

```bash
docker-compose up -d
```

This starts:
- **PostgreSQL** on `localhost:5432` (database: `orders`)
- **Kafka** on `localhost:9092`
- **Zookeeper** on `localhost:2181`

### 3. Run the application

```bash
# Using Maven wrapper (recommended)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or using Maven directly
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 4. Verify

```bash
# Health check
curl http://localhost:8080/actuator/health

# Create a test order
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: my-secret-key" \
  -d '{"customerId":"test-001","items":[{"productId":"P1","quantity":1,"unitPrice":9.99}]}'
```

## IDE Setup

### IntelliJ IDEA

1. **Open project**: File → Open → select `pom.xml` → Open as Project
2. **Enable annotation processing**: Settings → Build → Compiler → Annotation Processors → Enable
3. **Configure Lombok plugin**: Settings → Plugins → install Lombok
4. **Set JDK 21**: Project Structure → Project SDK → select Java 21
5. **Run configuration**: Spring Boot → create new → select `OrderServiceApplication`
   - Active profiles: `dev`

### VS Code

1. **Install extensions**:
   - Extension Pack for Java
   - Spring Boot Extension Pack
   - Lombok Annotations Support
2. **Open folder**: File → Open Folder → select project root
3. **Run**: Use the Spring Boot dashboard or `F5` on `OrderServiceApplication.java`

## Running Tests

### Unit tests only

```bash
./mvnw test
```

### Full verification (unit + integration + coverage)

```bash
./mvnw verify -Dspring.profiles.active=test
```

This runs:
1. Compile
2. Unit tests (Testcontainers auto-start PostgreSQL + Kafka)
3. Integration tests
4. JaCoCo coverage report
5. Coverage threshold check (≥70% line coverage)

### Run a single test class

```bash
./mvnw test -Dtest=OrderServiceTest
```

### Run a single test method

```bash
./mvnw test -Dtest=OrderServiceTest#shouldCreateOrder
```

### Test profiles

| Profile | Database | Kafka | Notes |
|---------|----------|-------|-------|
| `test` | Testcontainers PostgreSQL | Testcontainers Kafka | Default for `mvn test` |
| `dev` | Docker Compose PostgreSQL | Docker Compose Kafka | Local development |
| `prod` | External PostgreSQL | External Kafka | Production |

## Test Structure

```
src/test/java/com/igorservice/orderservice/
├── builder/
│   └── OrderTestDataBuilder.java       # Fluent test data factory
├── config/
│   └── ApiKeyAuthFilterTest.java       # Security filter unit tests
├── controller/
│   ├── OrderControllerTest.java        # MockMvc integration tests
│   └── OrderSearchControllerTest.java  # Search endpoint tests
├── exception/
│   └── GlobalExceptionHandlerTest.java # Exception handler tests
├── integration/
│   ├── OrderRepositoryIntegrationTest.java  # DB integration tests
│   └── OrderSearchIntegrationTest.java      # Search integration tests
├── metrics/
│   └── OrderMetricsTest.java           # Metrics recording tests
├── model/
│   ├── OrderItemTest.java              # OrderItem behavior tests
│   ├── OrderStatusTest.java            # State machine transition tests
│   └── OrderTest.java                  # Order entity behavior tests
└── service/
    ├── KafkaDlqListenerTest.java       # DLQ consumer tests
    ├── KafkaEventPublisherTest.java    # Event publishing tests
    └── OrderServiceTest.java           # Core business logic tests
```

### Using TestDataBuilder

```java
// Build an order with defaults
Order order = OrderTestDataBuilder.anOrder().build();

// Build with custom values
Order order = OrderTestDataBuilder.anOrder()
    .withCustomerId("CUST-123")
    .withStatus(OrderStatus.CONFIRMED)
    .build();

// Build a request
OrderRequest request = OrderTestDataBuilder.anOrderRequest()
    .withCustomerId("CUST-123")
    .withItems(List.of(
        OrderTestDataBuilder.anOrderItemRequest()
            .productId("PROD-001")
            .quantity(2)
            .unitPrice(BigDecimal.valueOf(29.99))
            .build()
    ))
    .build();
```

### Test conventions

1. **Given-When-Then** structure for all test methods
2. **`@DisplayName`** for readable test names
3. **Method naming**: `should{ExpectedBehavior}When{Condition}`
4. **One assertion per concept** (but multiple related assertions are OK)
5. **Arrange-Act-Assert** within Given-When-Then blocks

```java
@Test
@DisplayName("Should reject status transition from DELIVERED to PENDING")
void shouldRejectInvalidTransition() {
    // Given
    Order order = OrderTestDataBuilder.anOrder()
        .withStatus(OrderStatus.DELIVERED)
        .build();

    // When
    boolean canTransition = order.getStatus().canTransitionTo(OrderStatus.PENDING);

    // Then
    assertThat(canTransition).isFalse();
}
```

## Adding New Endpoints

### Step-by-step

1. **Create DTOs** (if needed) in `src/main/java/.../dto/`
   - Use `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
   - Add `@Schema` annotations for OpenAPI

2. **Add service method** in `OrderService.java` (or create new service)
   - Annotate with `@Transactional` (read-only for queries)
   - Use `@Slf4j` for logging
   - Set MDC context for tracing
   - Record metrics via `OrderMetrics`

3. **Add controller route** in `OrderController.java`
   - Annotate with `@Operation` for OpenAPI
   - Use `@Valid` for request validation
   - Return `ResponseEntity` with appropriate status code

4. **Handle exceptions** in `GlobalExceptionHandler.java`
   - Add `@ExceptionHandler` method for new exception types
   - Return structured error response

5. **Write tests**
   - Unit test in `*ServiceTest.java`
   - MockMvc test in `*ControllerTest.java`
   - Use `@DisplayName` for test names

6. **Update documentation**
   - Update `docs/api.md` with new endpoint
   - Update `README.md` API table if needed

## Adding New Kafka Events

### Step-by-step

1. **Define topic constant** in `KafkaEventPublisher.java`
   ```java
   private static final String TOPIC_ORDER_SHIPPED = "order.shipped";
   ```

2. **Add publish method** with `@Retryable`
   ```java
   @Retryable(
       value = {Exception.class},
       maxAttempts = 3,
       backoff = @Backoff(initialDelay = 1000, maxDelay = 10000, multiplier = 2.0)
   )
   public void publishOrderShipped(Order order) {
       var event = buildEvent(order, "ORDER_SHIPPED");
       publishSync(TOPIC_ORDER_SHIPPED, order.getId().toString(), event);
   }
   ```

3. **Add recovery method** with `@Recover`
   ```java
   @Recover
   public void recoverPublishOrderShipped(Exception ex, Order order) {
       log.error("All retries exhausted for ORDER_SHIPPED: {}", order.getOrderNumber(), ex);
       sendToDlq(TOPIC_ORDER_SHIPPED, order.getId().toString(),
           buildEvent(order, "ORDER_SHIPPED"), ex);
   }
   ```

4. **Call from OrderService** at the appropriate state transition
   ```java
   kafkaEventPublisher.publishOrderShipped(saved);
   ```

5. **Write tests** in `KafkaEventPublisherTest.java`

## Database Migrations

Flyway manages database schema evolution.

### Creating a migration

1. Create a new SQL file in `src/main/resources/db/migration/`
2. Name format: `V{N}__description.sql` (e.g., `V2__add_shipping_fields.sql`)
3. Write SQL statements
4. Test with `mvn flyway:migrate`

### Example

```sql
-- V2__add_shipping_fields.sql
ALTER TABLE orders ADD COLUMN shipped_at TIMESTAMP;
ALTER TABLE orders ADD COLUMN tracking_number VARCHAR(100);
```

### Rules

- **Never modify** existing migration files
- **Always additive** — don't drop columns in production
- **Test locally** before committing
- **Flyway is disabled** in test profile (uses `create-drop` instead)

## Code Style Conventions

### Java

- **Lombok**: `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`
- **Records**: Use for immutable DTOs
- **Streams**: Prefer over imperative loops
- **var**: Use for local variable type inference
- **Optional**: Don't use for method parameters

### Naming

- **Classes**: PascalCase (`OrderService`, `KafkaEventPublisher`)
- **Methods**: camelCase (`createOrder`, `getOrderById`)
- **Constants**: UPPER_SNAKE_CASE (`TOPIC_ORDER_CREATED`)
- **Packages**: lowercase (`com.igorservice.orderservice.service`)

### Logging

- Use `@Slf4j` (Lombok) instead of manual logger creation
- Include context: `log.info("Order created: {}", order.getOrderNumber())`
- Use appropriate levels:
  - `log.error()` — failures requiring attention
  - `log.warn()` — unexpected but recoverable situations
  - `log.info()` — business events (order created, status changed)
  - `log.debug()` — detailed operational info

### Exception Handling

- Custom exceptions extend `RuntimeException`
- All handled by `GlobalExceptionHandler`
- Return structured JSON error responses
- Include meaningful error messages

## Troubleshooting

### Application won't start

**Problem**: `Connection refused` to PostgreSQL

```bash
# Check if PostgreSQL is running
docker-compose ps

# Restart infrastructure
docker-compose down && docker-compose up -d
```

**Problem**: `Connection refused` to Kafka

```bash
# Check Kafka health
docker-compose logs kafka

# Wait for Kafka to be ready (may take 30s+)
docker-compose up -d
sleep 30
docker-compose ps
```

### Tests failing

**Problem**: `TestcontainersException` — Docker not running

```bash
# Ensure Docker Desktop is running
docker info
```

**Problem**: Port already in use

```bash
# Find and kill the process
lsof -i :5432
kill -9 <PID>
```

### Build failures

**Problem**: `Could not find artifact` or dependency issues

```bash
# Clean and rebuild
./mvnw clean install -DskipTests

# Force update dependencies
./mvnw clean install -U -DskipTests
```

### Kafka issues

**Problem**: `org.apache.kafka.common.errors.TimeoutException`

```bash
# Check Kafka is running and reachable
docker-compose logs kafka | tail -20

# Create topic manually (if auto-create is disabled)
docker-compose exec kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create --topic order.created --partitions 3
```

### Coverage below threshold

```bash
# Check current coverage
./mvnw verify -Dspring.profiles.active=test
# Open target/site/jacoco/index.html for details

# Common fixes:
# - Add tests for uncovered branches
# - Remove dead code
# - Refactor complex methods
```

## Useful Commands

```bash
# Full build with tests
./mvnw clean verify -Dspring.profiles.active=test

# Skip tests (for quick builds)
./mvnw clean package -DskipTests

# Run specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# Database migration
./mvnw flyway:migrate -Dspring.profiles.active=dev

# Generate OpenAPI spec
curl http://localhost:8080/v3/api-docs | python -m json.tool

# Docker build
docker build -t order-service .

# View logs (Docker Compose)
docker-compose logs -f order-service
```

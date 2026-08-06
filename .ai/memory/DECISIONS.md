# Architectural Decision Records (ADR)

> Records of key architectural decisions made in the order-service project.

---

### ADR-001: Java 21 + Spring Boot 3.2.5

**Status:** Accepted

**Context:** The project needed a modern, LTS Java version with strong virtual threads and pattern matching support, paired with a stable Spring Boot release that supports Jakarta EE (not javax).

**Decision:** Use Java 21 (Eclipse Temurin) as the runtime and Spring Boot 3.2.5 as the framework.

**Consequences:**
- Access to Java 21 features: pattern matching (`instanceof` with patterns), switch expressions, virtual threads (available but not yet used)
- Spring Boot 3.2.5 uses Jakarta EE 10 (jakarta.* namespace, not javax.*)
- Docker image uses `eclipse-temurin:21-jre` for runtime
- CI pipeline uses `actions/setup-java@v4` with `java-version: '21'`
- Flyway 9.x is compatible (no separate flyway-database-postgresql dependency needed)

---

### ADR-002: API Key Authentication via X-API-KEY Header

**Status:** Accepted

**Context:** The service needs a simple, stateless authentication mechanism for service-to-service or client-to-service calls without the complexity of JWT token management or OAuth flows.

**Decision:** Implement a custom `ApiKeyAuthFilter` (extends `OncePerRequestFilter`) that validates a static API key passed via the `X-API-KEY` HTTP header. The key is configured via `app.api-key` property.

**Consequences:**
- Simple to implement and debug — single header check against a configured value
- Stateless — no token generation, validation, or refresh logic needed
- No user identity or role-based access control — all authenticated requests have the same permissions
- Excluded paths: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- SecurityConfig disables CSRF (stateless API) and sets session management to STATELESS
- Key is stored in application.yml (dev: hardcoded, prod: from environment variable `${APP_API_KEY}`)
- Downside: key rotation requires redeployment or environment variable update
- Downside: no per-user auditing capability

---

### ADR-003: Apache Kafka for Event Streaming

**Status:** Accepted

**Context:** The order service needs to publish domain events (order created, status changed, cancelled) to downstream consumers without tight coupling.

**Decision:** Use Apache Kafka (Spring Kafka 3.1.5) for asynchronous event publishing with a fire-and-forget pattern.

**Consequences:**
- Three event topics: `order.created`, `order.status-changed`, `order.cancelled`
- One DLQ topic: `order.dlq`
- Events are published as JSON maps (not Avro/Protobuf — no schema registry)
- Producer configured with `acks=all`, idempotent mode, 3 retries
- Kafka publish failures do NOT fail the API call (fire-and-forget with retry fallback)
- Local development uses Confluent Docker images (Kafka 7.6.0 + Zookeeper)
- Auto-topic creation enabled in dev/test
- No consumer group in the service itself (only DLQ listener)

---

### ADR-004: Spring Retry with Exponential Backoff + DLQ Fallback

**Status:** Accepted

**Context:** Kafka operations may transiently fail (broker unavailable, network issues). The system needs resilience without failing the user-facing API call.

**Decision:** Use Spring Retry (`@Retryable`) with exponential backoff (3 attempts: 1s → 2s → 4s, max 10s) on all Kafka publish methods. On exhaustion, `@Recover` methods send the failed event to the `order.dlq` topic.

**Consequences:**
- Retry config: initial delay 1000ms, max delay 10000ms, multiplier 2.0, max 3 attempts
- `RetryConfig` bean provides a shared `RetryTemplate` with `ExponentialBackOffPolicy` and `SimpleRetryPolicy`
- `publishSync()` uses `CompletableFuture.join()` to block and allow retry to catch exceptions
- DLQ events include metadata: `dlq.originalTopic`, `dlq.failureReason`, `dlq.failedAt`
- DLQ listener (`KafkaDlqListener`) logs events for manual investigation
- No automatic retry from DLQ (manual intervention required)
- TODO: Alerting / metrics integration for DLQ events

---

### ADR-005: JPA Specifications for Dynamic Search

**Status:** Accepted

**Context:** Order search needs to support multiple optional filters (date range, status, customer ID) in any combination, producing dynamic SQL queries at runtime.

**Decision:** Use JPA `Specification<Order>` with `JpaSpecificationExecutor` for dynamic query building, instead of QueryDSL or Spring Data Query Methods.

**Consequences:**
- `OrderSearchService.buildSpecification()` constructs predicates using `CriteriaBuilder`
- Supports flexible combinations: startDate only, endDate only, both, status, customerId
- Predicates combined with `CriteriaBuilder.and()`
- Repository extends both `JpaRepository<Order, UUID>` and `JpaSpecificationExecutor<Order>`
- Query DSL alternative would require more generated code and dependency
- Spring Data Query Methods would require many method signatures for every filter combination
- Specifications are type-safe and composable

---

### ADR-006: Flyway for Database Migrations

**Status:** Accepted

**Context:** The database schema needs version-controlled, repeatable migrations that work across dev, test, and production environments.

**Decision:** Use Flyway (bundled with Spring Boot) for database schema migration management.

**Consequences:**
- Migration files in `src/main/resources/db/migration/` (currently `V1__create_orders_table.sql`)
- Flyway enabled in dev profile (`ddl-auto: validate`), disabled in test profile (`ddl-auto: create-drop`)
- `baseline-on-migrate: true` handles existing databases
- Only `flyway-core` dependency needed (PostgreSQL support built into Flyway 9.x)
- No separate `flyway-database-postgresql` dependency (was removed after initial build issues)
- Schema changes must be additive (no destructive DDL in production)

---

### ADR-007: PostgreSQL 16 as Database

**Status:** Accepted

**Context:** Need a production-grade relational database with JSON support, indexing, and strong consistency guarantees.

**Decision:** Use PostgreSQL 16 (Alpine image for Docker) as the primary datastore.

**Consequences:**
- Database name: `orders`
- Tables: `orders`, `order_items`
- UUID primary keys with `gen_random_uuid()` default
- Indexes on `customer_id`, `status`, `created_at`, `order_items.order_id`
- HikariCP connection pool: 20 max (dev), 50 max (prod)
- Hibernate dialect: `org.hibernate.dialect.PostgreSQLDialect`
- UTC timezone enforced via `hibernate.jdbc.time_zone: UTC`

---

### ADR-008: Micrometer + Prometheus for Metrics

**Status:** Accepted

**Context:** The service needs observable metrics for monitoring, alerting, and performance analysis.

**Decision:** Use Micrometer with Prometheus registry for metrics collection and export.

**Consequences:**
- Custom metrics in `OrderMetrics` component:
  - `orders.created.total` — Counter of all orders created
  - `order.creation.duration` — Timer with p50/p95/p99 percentiles
  - `orders.active.count` — Gauge of non-terminal orders (AtomicLong)
  - `orders.status.changed.total` — Counter tagged by status
- Actuator endpoints exposed: health, info, metrics, prometheus
- Prometheus scrape endpoint: `/actuator/prometheus`
- KafkaHealthIndicator provides custom Kafka health check with cluster ID and node count

---

### ADR-009: Lombok for Boilerplate Reduction

**Status:** Accepted

**Context:** Java entities and DTOs require significant boilerplate (getters, setters, builders, constructors, toString, equals, hashCode).

**Decision:** Use Lombok annotations (`@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`) to eliminate boilerplate.

**Consequences:**
- Entities: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
- DTOs: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`
- Services: `@RequiredArgsConstructor @Slf4j`
- Lombok excluded from final JAR via spring-boot-maven-plugin configuration
- Annotation processors run at compile time — no runtime dependency

---

### ADR-010: Lombok @Data for DTOs (Not Java Records)

**Status:** Accepted

**Context:** DTOs need to be serializable/deserializable by Jackson and support Swagger annotations, which can be awkward with Java records.

**Decision:** Use Lombok `@Data` classes for DTOs (`OrderRequest`, `OrderResponse`), not Java records. Exception: inner `StatusUpdateRequest` uses a record.

**Consequences:**
- `OrderRequest` and `OrderResponse` are `@Data` classes with `@Builder`
- Supports `@Schema` annotations from SpringDoc OpenAPI
- Mutable (setters available), though usage is primarily through builders
- `OrderResponse.fromEntity()` static factory method converts JPA entities to DTOs
- Inner `OrderItemResponse` is also a `@Data` class with `fromEntity()`
- `StatusUpdateRequest` is a record (simple, no builder needed)

---

### ADR-011: UUID for Primary Keys

**Status:** Accepted

**Context:** Entities need globally unique identifiers that work across distributed systems and don't leak business information.

**Decision:** Use `java.util.UUID` for all entity primary keys, generated by JPA with `GenerationType.UUID`.

**Consequences:**
- `Order.id`, `OrderItem.id` are `UUID` type
- Database uses `gen_random_uuid()` as default
- No sequential ID exposure (security benefit)
- UUIDs are 128-bit — slightly larger than BIGINT but acceptable
- API endpoints accept/return UUID strings (e.g., `/api/v1/orders/{orderId}`)
- Kafka event keys use `order.getId().toString()`

---

### ADR-012: java.time.Instant for Timestamps

**Status:** Accepted

**Context:** Timestamps need to be timezone-independent and work consistently across application layers and database.

**Decision:** Use `java.time.Instant` for all timestamp fields (`createdAt`, `updatedAt`).

**Consequences:**
- Instant represents a point on the UTC timeline — no timezone ambiguity
- Hibernate `@CreationTimestamp` auto-sets `createdAt` on persist
- Hibernate `@UpdateTimestamp` auto-sets `updatedAt` on every update
- Jackson configured with `write-dates-as-timestamps: false` for ISO 8601 serialization
- Database column type: `TIMESTAMP` (PostgreSQL stores as UTC)
- Hibernate timezone: `hibernate.jdbc.time_zone: UTC`

---

### ADR-013: Optimistic Locking via @Version

**Status:** Accepted

**Context:** Multiple concurrent requests may try to update the same order, leading to lost updates.

**Decision:** Use JPA `@Version` annotation on `Order.version` field for optimistic locking.

**Consequences:**
- `Order.version` is a `Long` field, auto-incremented by Hibernate on each update
- Concurrent updates to the same order cause `OptimisticLockException`
- No pessimistic locks (no SELECT FOR UPDATE)
- Database column: `version BIGINT NOT NULL DEFAULT 0`
- Works well for low-contention scenarios (typical for order updates)

---

### ADR-014: Soft Delete via CANCELLED Status

**Status:** Accepted

**Context:** Orders need to be "cancelled" without losing historical data for audit and reporting.

**Decision:** Orders are never physically deleted. The DELETE endpoint transitions order status to `CANCELLED`.

**Consequences:**
- `DELETE /api/v1/orders/{orderId}` sets status to CANCELLED (returns 204 No Content)
- No `deletedAt` or `isDeleted` flag — status IS the deletion indicator
- `CANCELLED` is a terminal state (no further transitions allowed)
- Query filtering: active orders can be queried by excluding CANCELLED status
- Audit trail preserved: all order data remains in the database
- `CascadeType.ALL` + `orphanRemoval = true` on Order.items means cancelling doesn't delete items

---

### ADR-015: Order Number Format ORD-{yyyyMMdd}-{sequence}

**Status:** Accepted

**Context:** Orders need human-readable, unique identifiers that embed creation date for quick reference.

**Decision:** Generate order numbers as `ORD-{yyyyMMdd}-{5-digit-sequence}` using an in-memory `AtomicLong` counter.

**Consequences:**
- Format: `ORD-20260806-00001`
- Sequence resets daily (new `AtomicLong(0)` each JVM start — not persisted)
- 5-digit zero-padded sequence (`%05d`)
- Date uses `LocalDate.now(ZoneOffset.UTC)` for consistency
- Counter is NOT persisted — sequence resets on application restart
- For production: consider database sequence or Redis-based counter for persistence
- Unique constraint on `order_number` column prevents duplicates

---

### ADR-016: Structured JSON Logging via Logstash Logback Encoder

**Status:** Accepted

**Context:** Production logs need to be machine-parseable for log aggregation systems (ELK, Datadog, etc.).

**Decision:** Use `logstash-logback-encoder` (7.4) for structured JSON logging in production, with colored console output for dev/test.

**Consequences:**
- Profile-based configuration in `logback-spring.xml`
- **dev/test**: Colored console output with MDC traceId/spanId
- **prod**: JSON output via `LogstashEncoder` for log aggregation
- MDC fields: `traceId` (UUID), `spanId` (8-char substring)
- Log level pattern: `%5p [${spring.application.name},%X{traceId},%X{spanId}]`
- Production log level: INFO for app, WARN for root

---

### ADR-017: Testcontainers for Integration Tests

**Status:** Accepted

**Context:** Integration tests need real PostgreSQL and Kafka instances without requiring manual setup or shared infrastructure.

**Decision:** Use Testcontainers (1.19.8) with PostgreSQL and Kafka modules for integration tests.

**Consequences:**
- Test profile uses `org.testcontainers.jdbc.ContainerDatabaseDriver` for PostgreSQL
- JDBC URL: `jdbc:tc:postgresql:16:///orders` (auto-provisions container)
- Kafka broker provided by `${spring.embedded.kafka.brokers}` in test profile
- Flyway disabled in test profile (`ddl-auto: create-drop`)
- Tests are self-contained — no external infrastructure needed
- Containers start/stop per test class (or per method depending on config)

---

### ADR-018: JaCoCo 70% Line Coverage Threshold

**Status:** Accepted

**Context:** Code quality needs a minimum test coverage standard to prevent regression.

**Decision:** Configure JaCoCo Maven plugin (0.8.12) with a 70% minimum line coverage threshold on the BUNDLE element.

**Consequences:**
- JaCoCo configured with `prepare-agent`, `report`, and `check` goals
- `check` phase runs during `verify` — build fails if coverage drops below 70%
- Coverage report generated at `target/site/jacoco/`
- CI uploads coverage report as artifact (14-day retention)
- Currently NOT enforced in CI (PLAN.md marks it as pending)
- Coverage enforcement is in pom.xml but CI `verify` step may not trigger it reliably

---

### ADR-019: Multi-Stage Docker Build

**Status:** Accepted

**Context:** Docker images need to be small, secure, and reproducible.

**Decision:** Use a multi-stage Dockerfile: Maven builder stage + JRE runtime stage.

**Consequences:**
- **Builder stage**: `maven:3.9-eclipse-temurin-21` — compiles and packages JAR
- **Runtime stage**: `eclipse-temurin:21-jre` — minimal JRE image
- Dependencies cached separately (`mvn dependency:go-offline`) for layer caching
- Non-root user (`appuser`) for security
- `curl` installed for HEALTHCHECK (not included in eclipse-temurin JRE)
- JVM tuning: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0`
- HEALTHCHECK: `curl -f http://localhost:8080/actuator/health`
- Final image excludes Lombok and test dependencies

---

### ADR-020: JPA Entity Indexes

**Status:** Accepted

**Context:** Queries on `customer_id`, `status`, and `created_at` columns need to be performant at scale.

**Decision:** Add database indexes on frequently queried columns via JPA `@Index` annotations and Flyway migration.

**Consequences:**
- `idx_orders_customer_id` on `orders(customer_id)` — for customer order lookups
- `idx_orders_status` on `orders(status)` — for status-based filtering
- `idx_orders_created_at` on `orders(created_at)` — for date range queries
- `idx_order_items_order_id` on `order_items(order_id)` — for order item lookups
- Indexes defined in both JPA `@Table(indexes = {...})` and Flyway `V1__create_orders_table.sql`
- `ddl-auto: validate` ensures JPA indexes match database indexes

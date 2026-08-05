# Order Service — Implementation Roadmap

## Phase 1: Foundation (Week 1-2)

- [x] Project structure and pom.xml
- [x] Spring Boot application config
- [x] Docker Compose (PostgreSQL, Kafka, Zookeeper)
- [x] Flyway migrations
- [x] JPA entities (Order, OrderItem)
- [x] OrderRepository
- [x] Spring Security + API key auth

## Phase 2: Core Business Logic (Week 2-3)

- [x] OrderService (CRUD + status machine)
- [x] DTOs (OrderRequest, OrderResponse)
- [x] OrderController (REST endpoints)
- [x] GlobalExceptionHandler
- [x] Order number generation optimization (atomic counter + date-based)
- [x] Custom repository queries (findByCustomerId, findByStatus, countByStatus, findByCreatedAtBetween)

## Phase 3: Event-Driven Architecture (Week 3-4)

- [x] KafkaConfig (producer)
- [x] KafkaEventPublisher (3 event types)
- [x] Retry logic with backoff (Spring Retry, 3 attempts, exponential)
- [x] Dead-letter queue (order.dlq topic + @Recover methods)
- [ ] Event schemas (JSON Schema / Avro)

## Phase 4: Observability (Week 4-5)

- [x] Micrometer + Prometheus
- [x] Actuator health endpoints
- [x] Custom metrics (orders created, status changes, active count gauge)
- [x] Structured JSON logging (logstash-logback-encoder, profile-based)
- [x] Distributed tracing (MDC with traceId/spanId)
- [x] OrderMetrics component with Counter, Timer, Gauge

## Phase 5: Testing (Week 5-6)

- [x] OrderService unit tests
- [x] OrderController MockMvc tests
- [x] OrderMetrics unit tests (counters, timer, gauge)
- [x] KafkaEventPublisher tests (mock KafkaTemplate)
- [x] Testcontainers integration tests (OrderRepositoryIntegrationTest)
- [ ] Test data builders

## Phase 6: Production Readiness (Week 6-7)

- [x] Multi-stage Dockerfile
- [x] GitHub Actions CI/CD
- [ ] JaCoCo code coverage
- [ ] OWASP security scanning
- [ ] Kubernetes manifests (Helm)

## Phase 7: Enhancements (Week 7+)

- [ ] Redis caching for hot reads
- [ ] Order search by date range
- [ ] Bulk operations
- [ ] Webhook notifications

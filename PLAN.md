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
- [ ] Order number generation optimization
- [ ] Custom repository queries

## Phase 3: Event-Driven Architecture (Week 3-4)

- [x] KafkaConfig (producer)
- [x] KafkaEventPublisher (3 event types)
- [ ] Event schemas (JSON Schema / Avro)
- [ ] Retry logic with backoff
- [ ] Dead-letter queue

## Phase 4: Observability (Week 4-5)

- [x] Micrometer + Prometheus
- [x] Actuator health endpoints
- [ ] Custom metrics (orders created, status changes)
- [ ] Structured JSON logging
- [ ] Distributed tracing (MDC)

## Phase 5: Testing (Week 5-6)

- [x] OrderService unit tests
- [x] OrderController MockMvc tests
- [ ] KafkaEventPublisher tests
- [ ] Testcontainers integration tests
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

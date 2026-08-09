# Order Service

[![CI](https://github.com/igorVilela7713/order-service/actions/workflows/ci.yml/badge.svg)](https://github.com/igorVilela7713/order-service/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/coverage-%3E%3D70%25-brightgreen)](https://github.com/igorVilela7713/order-service)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## Overview

A Spring Boot 3 microservice for order processing with event-driven architecture using Apache Kafka. Provides RESTful CRUD operations with API key authentication, structured logging, Prometheus metrics, and OpenAPI documentation.

## Architecture

```
                    ┌──────────────────────────────────────────────────┐
                    │                    CLIENT                        │
                    └──────────────────────┬───────────────────────────┘
                                           │ HTTP (X-API-KEY)
                    ┌──────────────────────▼───────────────────────────┐
                    │              OrderController                      │
                    │         /api/v1/orders/*                          │
                    └──────┬───────────┬──────────────┬────────────────┘
                           │           │              │
                    ┌──────▼──┐  ┌──────▼──────┐  ┌───▼──────────┐
                    │ Order   │  │ OrderSearch │  │   Global      │
                    │ Service │  │ Service     │  │   Exception   │
                    └──┬──┬───┘  └──────┬──────┘  └──────────────┘
                       │  │             │
            ┌──────────┘  │    ┌────────┘
            │             │    │
     ┌──────▼──────┐ ┌───▼────▼─────┐ ┌────────────────┐
     │   Order     │ │   Order      │ │   KafkaEvent   │
     │ Repository  │ │ Specification│ │   Publisher    │
     └──────┬──────┘ └──────────────┘ └───────┬────────┘
            │                                  │
     ┌──────▼──────┐                    ┌──────▼──────┐
     │ PostgreSQL  │                    │    Kafka     │
     │  (Flyway)   │                    │  (3 topics)  │
     └─────────────┘                    └──────┬──────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │  Downstream Systems  │
                                    │  (DLQ for failures)  │
                                    └─────────────────────┘
```

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Runtime |
| Spring Boot | 3.2.5 | Framework |
| Spring Data JPA | - | ORM / Repository |
| PostgreSQL | 16 | Primary database |
| Flyway | - | Database migrations |
| Apache Kafka | 3.6+ | Event streaming |
| Spring Security | - | API key auth |
| Springdoc OpenAPI | 2.5.0 | Swagger UI |
| Micrometer + Prometheus | - | Metrics |
| Logstash Logback Encoder | 7.4 | Structured logging |
| Lombok | - | Boilerplate reduction |
| Testcontainers | 1.19.8 | Integration testing |
| JaCoCo | 0.8.12 | Code coverage |

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker-compose up -d
```

This starts PostgreSQL, Kafka, and Zookeeper.

### 2. Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Create an order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: my-secret-key" \
  -d '{
    "customerId": "customer-001",
    "items": [
      {"productId": "PROD-001", "productName": "Widget Pro", "quantity": 2, "unitPrice": 29.99},
      {"productId": "PROD-002", "productName": "Gadget Plus", "quantity": 1, "unitPrice": 49.99}
    ]
  }'
```

### 4. Browse API docs

Open http://localhost:8080/swagger-ui.html in your browser.

## API Reference

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| `POST` | `/api/v1/orders` | Create a new order | ✅ |
| `GET` | `/api/v1/orders` | List all orders (paginated) | ✅ |
| `GET` | `/api/v1/orders/{id}` | Get order by ID | ✅ |
| `PUT` | `/api/v1/orders/{id}/status` | Update order status | ✅ |
| `DELETE` | `/api/v1/orders/{id}` | Cancel an order | ✅ |
| `GET` | `/api/v1/orders/search` | Search orders with filters | ✅ |
| `GET` | `/actuator/health` | Health check | ❌ |
| `GET` | `/actuator/prometheus` | Prometheus metrics | ❌ |
| `GET` | `/swagger-ui.html` | Swagger UI | ❌ |

See [docs/api.md](docs/api.md) for complete API documentation with examples.

## Cancel Order Feature

The order cancellation feature provides a soft-delete mechanism where orders are never physically removed from the database — they transition to `CANCELLED` status.

### Endpoint

**DELETE** `/api/v1/orders/{orderId}`

### Valid State Transitions

Only orders in these statuses can be cancelled:

```
PENDING → CANCELLED
CONFIRMED → CANCELLED
PROCESSING → CANCELLED
```

Orders in `SHIPPED`, `DELIVERED`, or already `CANCELLED` status cannot be cancelled.

### Side Effects

When an order is cancelled, the following happens atomically within a transaction:

1. **Status change**: Order status transitions to `CANCELLED`
2. **Event publishing**: `ORDER_CANCELLED` event published to Kafka topic `order.cancelled`
3. **Metrics recorded**:
   - `orders.status.changed.total{status="CANCELLED"}` counter incremented
   - `orders.active.count` gauge decremented (order moves to terminal state)
4. **Distributed tracing**: MDC trace context logged with traceId/spanId

### Example Request

```bash
curl -X DELETE http://localhost:8080/api/v1/orders/550e8400-e29b-41d4-a716-446655440000 \
  -H "X-API-KEY: ***"
```

### Response

- **204 No Content** — Order cancelled successfully

### Error Responses

**400 Bad Request** — Invalid state transition:
```json
{
  "status": 400,
  "error": "Illegal State",
  "message": "Order cannot be cancelled in status: DELIVERED"
}
```

**404 Not Found** — Order doesn't exist:
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Order not found with ID: 550e8400-e29b-41d4-a716-446655440000"
}
```

### Event Payload (order.cancelled topic)

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "ORDER_CANCELLED",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "orderNumber": "ORD-20260805-00001",
  "customerId": "customer-001",
  "totalAmount": 59.98,
  "status": "CANCELLED",
  "timestamp": "2026-08-05T10:30:00Z"
}
```

### Resilience & DLQ

- Kafka publish is `@Retryable` with 3 attempts, exponential backoff (1s → 2s → 4s)
- On exhaustion, `@Recover` method sends event to Dead Letter Queue (`order.dlq`)
- DLQ messages include original topic, failure reason, and timestamp for investigation
- `KafkaDlqListener` logs DLQ events for manual retry/alerting

### Audit Logging

Every cancellation is recorded in the audit log for compliance and traceability:

**Audit Event**: `ORDER_CANCELLED`

**Stored Data**:
- Order ID, order number, customer ID, total amount
- Previous status (before cancellation) and new status (`CANCELLED`)
- Actor ID (trace ID from MDC context)
- Full order snapshot including items (JSON in `eventData` column)
- Timestamp of cancellation

**Retrieval Endpoints** (via `AuditLogService`):
- `GET /api/v1/orders/{orderId}/audit` — paginated audit trail for a specific order
- Filter by event type (`ORDER_CANCELLED`), date range, or recent events
- Count endpoints for dashboards: total cancellations per order, per event type

---

## Configuration

### Environment Variables

| Variable | Description | Default (dev) | Required (prod) |
|----------|-------------|---------------|-----------------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` | Yes |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/orders` | Yes |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` | Yes |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `postgres` | Yes |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address | `localhost:9092` | Yes |
| `APP_API_KEY` | API key for authentication | `my-secret-key` | Yes |
| `JAVA_OPTS` | JVM options | (see Dockerfile) | No |

### Profiles

| Profile | Description | ddl-auto | Logging |
|---------|-------------|----------|---------|
| `dev` | Local development | `update` | DEBUG |
| `test` | Integration tests (Testcontainers) | `create-drop` | DEBUG |
| `prod` | Production | `validate` | INFO (JSON) |

## Testing

### Run tests

```bash
# Unit tests only
./mvnw test

# Full verification (unit + integration + coverage check)
./mvnw verify -Dspring.profiles.active=test
```

### Test structure

```
src/test/java/.../
├── builder/
│   └── OrderTestDataBuilder.java      # Fluent test data builder
├── config/
│   └── ApiKeyAuthFilterTest.java      # Security filter tests
├── controller/
│   ├── OrderControllerTest.java       # MockMvc tests
│   └── OrderSearchControllerTest.java
├── exception/
│   └── GlobalExceptionHandlerTest.java
├── integration/
│   ├── OrderRepositoryIntegrationTest.java
│   └── OrderSearchIntegrationTest.java
├── metrics/
│   └── OrderMetricsTest.java
├── model/
│   ├── OrderItemTest.java
│   ├── OrderStatusTest.java           # State machine tests
│   └── OrderTest.java
└── service/
    ├── KafkaDlqListenerTest.java
    ├── KafkaEventPublisherTest.java
    └── OrderServiceTest.java
```

### Test patterns

- **Unit tests**: Mock dependencies, test single class behavior
- **Integration tests**: Use Testcontainers for real PostgreSQL/Kafka
- **Controller tests**: MockMvc with security filter configured
- **Coverage**: JaCoCo enforces minimum 70% line coverage

## Docker

### Build image

```bash
docker build -t order-service .
```

### Run with Docker Compose (full stack)

```bash
docker-compose up -d
```

### Run standalone

```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/orders \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -e APP_API_KEY=my-secret-key \
  order-service
```

## CI/CD

GitHub Actions pipeline (`.github/workflows/ci.yml`):

1. **Build & Test**: Compile → Verify (tests + integration tests)
2. **Code Quality**: Static analysis checks (no System.out, TODO tracking)
3. **Docker Build**: Build image → Health check with retry loop
4. **Artifacts**: Test results + JaCoCo coverage report uploaded

Coverage enforcement: JaCoCo checks minimum 70% line coverage during `mvn verify`.

## Documentation

| Document | Description |
|----------|-------------|
| [docs/api.md](docs/api.md) | Complete API reference with examples |
| [docs/architecture.md](docs/architecture.md) | System design and data flow |
| [docs/development.md](docs/development.md) | Developer setup and conventions |
| [AGENTS.md](AGENTS.md) | AI agent guidance for this codebase |
| [SPEC.md](SPEC.md) | Original project specification |
| [docs/memory.md](docs/memory.md) | Repository memory system (mandatory read/write flow) |



## Repository Memory System

This repository maintains a living knowledge base at [`MEMORY.md`](MEMORY.md) that accumulates context across agent sessions — conventions, known pitfalls, architecture decisions, verified commands and lessons learned. See [`docs/memory.md`](docs/memory.md) for full documentation.

**Mandatory flow for AI agents (Hermes, Claude, Copilot, etc.):**
1. **Read `MEMORY.md`** before starting any task.
2. **Update `MEMORY.md`** when you finish — recording pitfalls, decisions and commands you validated — and **commit** the update (`docs(memory): ...`), never leaving it uncommitted.

> Full format and flow are defined in [`AGENTS.md`](AGENTS.md).

## License

MIT

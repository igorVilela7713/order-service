# Architecture

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                            │
│  Web UI / Mobile App / External Service / CLI                   │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS + X-API-KEY
┌────────────────────────────▼────────────────────────────────────┐
│                        API LAYER                                │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │  Spring      │  │  Security    │  │  OpenAPI /         │    │
│  │  Security    │──│  Filter      │  │  Swagger UI        │    │
│  │  Filter Chain│  │  (API Key)   │  │                    │    │
│  └──────┬───────┘  └──────────────┘  └────────────────────┘    │
│         │                                                       │
│  ┌──────▼──────────────────────────────────────────────────┐    │
│  │              OrderController                             │    │
│  │  POST /orders  GET /orders  GET /orders/{id}            │    │
│  │  PUT /orders/{id}/status  DELETE /orders/{id}           │    │
│  │  GET /orders/search                                     │    │
│  └──────┬───────────────┬───────────────────┬──────────────┘    │
└─────────┼───────────────┼───────────────────┼──────────────────┘
          │               │                   │
┌─────────▼───────────────▼───────────────────▼──────────────────┐
│                      SERVICE LAYER                              │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │  OrderService     │  │  OrderSearch     │  │  OrderMetrics │  │
│  │  - createOrder()  │  │  Service         │  │  - counters   │  │
│  │  - getOrderById() │  │  - search()      │  │  - timers     │  │
│  │  - listOrders()   │  │  - Specification │  │  - gauges     │  │
│  │  - updateStatus() │  │    builder       │  │               │  │
│  │  - cancelOrder()  │  └──────────────────┘  └──────────────┘  │
│  └────────┬─────────┘                                           │
│           │                                                     │
│  ┌────────▼─────────────────────────────────────────────────┐   │
│  │              KafkaEventPublisher                          │   │
│  │  @Retryable (3 attempts, exponential backoff)            │   │
│  │  @Recover → send to DLQ topic                            │   │
│  └────────┬─────────────────────────────────────────────────┘   │
└───────────┼─────────────────────────────────────────────────────┘
            │
┌───────────▼─────────────────────────────────────────────────────┐
│                    DATA LAYER                                    │
│                                                                 │
│  ┌──────────────────┐              ┌──────────────────┐         │
│  │  OrderRepository  │              │  KafkaTemplate   │         │
│  │  (JPA + Specs)   │              │  (Producer)      │         │
│  └────────┬─────────┘              └────────┬─────────┘         │
│           │                                 │                   │
│  ┌────────▼─────────┐              ┌────────▼─────────┐         │
│  │  PostgreSQL 16   │              │  Kafka 3.6       │         │
│  │  - orders        │              │  - order.created  │         │
│  │  - order_items   │              │  - order.status-  │         │
│  │  (Flyway)        │              │    changed        │         │
│  └──────────────────┘              │  - order.cancelled│         │
│                                    │  - order.dlq      │         │
│                                    └──────────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

## Component Descriptions

### OrderController
- REST controller handling all order operations
- Delegates to `OrderService` for CRUD and `OrderSearchService` for search
- Uses `@Operation` annotations for OpenAPI documentation
- Pagination support with configurable page size (max 100)

### OrderService
- Core business logic for order lifecycle management
- Enforces status state machine transitions via `OrderStatus.canTransitionTo()`
- Uses MDC tracing (traceId, spanId) for distributed tracing
- Publishes Kafka events on state changes
- Records metrics via `OrderMetrics`
- Generates order numbers: `ORD-{yyyyMMdd}-{sequence}`

### OrderSearchService
- Dynamic query building using JPA `Specification<Order>`
- Supports multiple filter combinations (date range, status, customer)
- Returns paginated results sorted by creation date (newest first)

### KafkaEventPublisher
- Publishes events to three Kafka topics
- Uses `@Retryable` with exponential backoff (3 attempts, 1s→10s)
- On exhaustion, `@Recover` methods send to DLQ topic
- `publishSync()` blocks to allow retry propagation

### OrderMetrics
- Micrometer-based observability
- `orders.created.total` — Counter of orders created
- `order.creation.duration` — Timer (p50, p95, p99)
- `orders.active.count` — Gauge of non-terminal orders
- `orders.status.changed.total` — Counter tagged by status

### KafkaDlqListener
- Consumes from `order.dlq` topic
- Logs failed events for manual investigation
- Placeholder for alerting/metrics integration

## Data Flow

### Order Creation Flow

```
Client ──POST──→ OrderController
                    │
                    ▼
              OrderService.createOrder()
                    │
                    ├──► MDC.put("traceId", ...)
                    ├──► Order.builder() → build order
                    ├──► OrderRepository.save()
                    ├──► OrderMetrics.recordOrderCreated()
                    ├──► KafkaEventPublisher.publishOrderCreated()
                    │      │
                    │      ├──► @Retryable (up to 3 attempts)
                    │      │      │
                    │      │      ├──► kafkaTemplate.send("order.created", ...)
                    │      │      │      │
                    │      │      │      └──► CompletableFuture.join()
                    │      │      │
                    │      │      └──► On failure: retry with backoff
                    │      │
                    │      └──► @Recover (after 3 failures)
                    │             │
                    │             └──► sendToDlq("order.dlq", ...)
                    │
                    └──► OrderResponse.fromEntity()
                              │
                              ▼
                        HTTP 201 Created
```

### Order Status Update Flow

```
Client ──PUT──→ OrderController.updateOrderStatus()
                    │
                    ▼
              OrderService.updateOrderStatus()
                    │
                    ├──► OrderRepository.findById()
                    ├──► OrderStatus.canTransitionTo() — validate
                    ├──► Order.setStatus(newStatus)
                    ├──► OrderRepository.save()
                    ├──► OrderMetrics.recordStatusChanged()
                    ├──► OrderMetrics.recordOrderCompleted() (if terminal)
                    ├──► KafkaEventPublisher.publishOrderStatusChanged()
                    │      │
                    │      └──► @Retryable → @Recover → DLQ
                    │
                    └──► OrderResponse.fromEntity()
```

## Kafka Event Flow

### Topics

| Topic | Event Type | Trigger | Key |
|-------|------------|---------|-----|
| `order.created` | ORDER_CREATED | New order created | orderId |
| `order.status-changed` | ORDER_STATUS_CHANGED | Status updated | orderId |
| `order.cancelled` | ORDER_CANCELLED | Order cancelled | orderId |
| `order.dlq` | (original event + metadata) | All retries exhausted | orderId |

### Event Payload

```json
{
  "eventId": "uuid",
  "eventType": "ORDER_CREATED",
  "orderId": "uuid",
  "orderNumber": "ORD-20260805-00001",
  "customerId": "customer-001",
  "totalAmount": 59.98,
  "status": "PENDING",
  "timestamp": "2026-08-05T10:30:00Z"
}
```

Status change events also include `previousStatus`.

### DLQ Event

Failed events are enriched with DLQ metadata:

```json
{
  "eventId": "uuid",
  "eventType": "ORDER_CREATED",
  "orderId": "uuid",
  "dlq.originalTopic": "order.created",
  "dlq.failureReason": "Connection refused",
  "dlq.failedAt": "2026-08-05T10:35:00Z"
}
```

## Database Schema

### orders table

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, auto-generated |
| order_number | VARCHAR(50) | NOT NULL, UNIQUE |
| customer_id | VARCHAR(100) | NOT NULL |
| status | VARCHAR(20) | NOT NULL, enum string |
| total_amount | DECIMAL(12,2) | NOT NULL |
| created_at | TIMESTAMP | NOT NULL, auto-set |
| updated_at | TIMESTAMP | NOT NULL, auto-set |
| version | BIGINT | Optimistic locking |

**Indexes:**
- `idx_orders_customer_id` on `customerId`
- `idx_orders_status` on `status`
- `idx_orders_created_at` on `createdAt`

### order_items table

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PRIMARY KEY, auto-generated |
| order_id | UUID | FK → orders.id, NOT NULL |
| product_id | VARCHAR(100) | NOT NULL |
| product_name | VARCHAR(200) | |
| quantity | INTEGER | NOT NULL |
| unit_price | DECIMAL(12,2) | NOT NULL |
| total_price | DECIMAL(12,2) | NOT NULL |

## Security Model

### Authentication
- API key authentication via `X-API-KEY` header
- Implemented as a `OncePerRequestFilter` (`ApiKeyAuthFilter`)
- Stateless session management (no JWT/sessions)

### Authorization
| Path | Access |
|------|--------|
| `/actuator/**` | Public |
| `/swagger-ui/**` | Public |
| `/v3/api-docs/**` | Public |
| `OPTIONS /**` | Public (CORS preflight) |
| All other endpoints | API key required |

### CORS
- Allowed origins: `localhost:3000`, `localhost:8080`
- Allowed methods: GET, POST, PUT, DELETE, OPTIONS
- Credentials: allowed

## Observability Stack

### Metrics (Micrometer + Prometheus)

Exposed at `/actuator/prometheus`:

| Metric | Type | Description |
|--------|------|-------------|
| `orders.created.total` | Counter | Total orders created |
| `order.creation.duration` | Timer | Creation latency (p50/p95/p99) |
| `orders.active.count` | Gauge | Active (non-terminal) orders |
| `orders.status.changed.total` | Counter | Status changes (tagged by status) |

### Structured Logging

**Dev/Test profile:**
```
10:30:00.123 INFO  [thread] [traceId,spanId] c.i.o.s.OrderService - Creating order...
```

**Prod profile (JSON):**
```json
{
  "@timestamp": "2026-08-05T10:30:00.123Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "spanId": "a1b2c3d4",
  "message": "Creating order for customer: customer-001"
}
```

### Health Checks

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Composite health (DB + Kafka) |
| `/actuator/health/kafka` | Kafka broker connectivity |
| `KafkaHealthIndicator` | Uses `AdminClient` to verify cluster |

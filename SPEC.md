# Order Service — Architecture Specification

## Overview

The Order Service is a stateless Spring Boot microservice responsible for managing the lifecycle of customer orders. It persists order data to PostgreSQL, publishes domain events to Apache Kafka for downstream consumers (inventory, billing, notifications), and exposes a RESTful API with OpenAPI documentation.

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌────────────┐
│   Client     │────▶│  OrderController  │────▶│ OrderService│
│  (REST)      │     │  (Spring MVC)     │     │  (Business) │
└─────────────┘     └──────────────────┘     └─────┬──────┘
                                                    │
                                    ┌───────────────┼──────────────┐
                                    ▼               ▼              ▼
                              ┌──────────┐   ┌───────────┐  ┌──────────────┐
                              │ OrderRepo│   │  Kafka     │  │  Metrics     │
                              │ (JPA)    │   │  Publisher │  │  (Micrometer)│
                              └────┬─────┘   └─────┬─────┘  └──────────────┘
                                   ▼               ▼
                              ┌──────────┐   ┌───────────┐
                              │PostgreSQL│   │   Kafka   │
                              │          │   │  (Broker) │
                              └──────────┘   └─────┬─────┘
                                                   ▼
                                            ┌──────────────┐
                                            │  Downstream  │
                                            │  Consumers   │
                                            └──────────────┘
```

## API Endpoints

### Base URL: `/api/v1`

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `/orders` | Create a new order | `OrderRequest` | `OrderResponse` (201) |
| `GET` | `/orders` | List all orders (paginated) | — | `List<OrderResponse>` |
| `GET` | `/orders/{orderId}` | Get order by ID | — | `OrderResponse` |
| `PUT` | `/orders/{orderId}/status` | Update order status | `{"status": "..."}` | `OrderResponse` |
| `DELETE` | `/orders/{orderId}` | Cancel/delete order | — | `204 No Content` |

### Authentication

All endpoints require the `X-API-KEY` header.

## Data Models

### Order Entity

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK | Order identifier |
| `order_number` | `VARCHAR(50)` | UNIQUE, NOT NULL | Human-readable order number |
| `customer_id` | `VARCHAR(100)` | NOT NULL | Customer identifier |
| `status` | `VARCHAR(20)` | NOT NULL, DEFAULT 'PENDING' | Order status |
| `total_amount` | `DECIMAL(12,2)` | NOT NULL | Sum of item totals |
| `created_at` | `TIMESTAMP` | NOT NULL | Creation timestamp |
| `updated_at` | `TIMESTAMP` | NOT NULL | Last update timestamp |

### Order Item Entity

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `UUID` | PK | Item identifier |
| `order_id` | `UUID` | FK → orders.id | Parent order |
| `product_id` | `VARCHAR(100)` | NOT NULL | Product identifier |
| `quantity` | `INTEGER` | NOT NULL, > 0 | Quantity ordered |
| `unit_price` | `DECIMAL(12,2)` | NOT NULL | Price per unit |
| `total_price` | `DECIMAL(12,2)` | NOT NULL | quantity × unit_price |

### Order Status

```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
                    ↘ CANCELLED
```

## Event-Driven Design

### Kafka Topics

| Topic | Key | Description |
|-------|-----|-------------|
| `order.created` | `orderId` | Emitted when order is created |
| `order.status-changed` | `orderId` | Emitted on status transitions |
| `order.cancelled` | `orderId` | Emitted when order is cancelled |

## Security

- API key via `X-API-KEY` header
- Stateless authentication (no sessions)
- CORS configured per profile

## Observability

- Prometheus metrics at `/actuator/prometheus`
- Health checks at `/actuator/health`
- Structured logging with MDC (traceId, orderId)

## Error Handling

| Exception | HTTP Status |
|-----------|-------------|
| `OrderNotFoundException` | 404 |
| `IllegalStateException` | 409 |
| `MethodArgumentNotValidException` | 422 |
| `Exception` (fallback) | 500 |

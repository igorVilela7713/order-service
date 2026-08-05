# Order Service

A Spring Boot 3 microservice for order processing with event-driven architecture using Apache Kafka.

## Features

- RESTful CRUD API for order management
- Event-driven architecture with Apache Kafka
- PostgreSQL persistence with Flyway migrations
- Spring Security with API key authentication
- Actuator endpoints with Prometheus metrics
- OpenAPI 3.0 documentation (Swagger UI)
- Docker multi-stage build
- GitHub Actions CI/CD pipeline
- Testcontainers for integration testing

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL 16 (or use Docker Compose)
- Apache Kafka 3.6+ (or use Docker Compose)

## Quick Start

### 1. Start infrastructure

```bash
docker-compose up -d
```

### 2. Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Access endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/orders` | List all orders |
| `GET /api/v1/orders/{id}` | Get order by ID |
| `POST /api/v1/orders` | Create a new order |
| `PUT /api/v1/orders/{id}/status` | Update order status |
| `GET /swagger-ui.html` | Swagger UI |
| `GET /actuator/prometheus` | Prometheus metrics |

### 4. Create an order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: my-secret-key" \
  -d '{
    "customerId": "customer-001",
    "items": [
      {"productId": "PROD-001", "quantity": 2, "unitPrice": 29.99},
      {"productId": "PROD-002", "quantity": 1, "unitPrice": 49.99}
    ]
  }'
```

## Running Tests

```bash
# Unit tests
./mvnw test

# Full verification (unit + integration)
./mvnw verify
```

## Configuration

Environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `dev` |
| `SPRING_DATASOURCE_URL` | PostgreSQL URL | `jdbc:postgresql://localhost:5432/orders` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `postgres` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker | `localhost:9092` |
| `APP_API_KEY` | API key for auth | `my-secret-key` |

## Architecture

```
Client → OrderController → OrderService → OrderRepository → PostgreSQL
                        ↘ KafkaEventPublisher → Kafka → [downstream consumers]
```

## License

MIT

# MEMORY.md — Living Memory of the Repo

> **Working agreement (mandatory):** read this file before any task and append findings on completion. See `AGENTS.md` -> "Repository Memory System" and `docs/memory.md`.

## Project Summary
A Spring Boot 3.2.5 microservice for order processing with an event-driven architecture using Apache Kafka. Provides RESTful CRUD with API-key auth, Flyway migrations, structured JSON logging, Micrometer/Prometheus metrics, and OpenAPI docs.

## Stack
- Java 21, Spring Boot 3.2.5, Spring Data JPA
- PostgreSQL 16, Flyway
- Apache Kafka 3.6+ (Spring Kafka)
- Spring Security (API key), Springdoc OpenAPI 2.5.0
- Lombok, Micrometer + Prometheus, LogStash Logback Encoder
- JUnit 5, Mockito, Testcontainers, JaCoCo (>= 70% coverage)

## Conventions (quick reference)
- Lombok: `@Data`, `@Builder`, `@Slf4j`.
- Records for DTOs (immutable).
- `java.time.Instant` for timestamps (UTC); `UUID` for IDs.
- API versioning: `/api/v1/`. Given-When-Then tests with `@DisplayName`.
- Soft delete (status -> CANCELLED); `@Version` optimistic locking; OrderStatus state machine enforced in service layer.

## Verified Commands (build / test / deploy)
| Step | Command | Notes |
|------|---------|-------|
| Infra | `docker-compose up -d` | PostgreSQL + Kafka + Zookeeper |
| Run (dev) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | |
| Unit tests | `./mvnw test` | |
| Verify | `./mvnw verify -Dspring.profiles.active=test` | unit + integration + coverage |
| Docker | `docker build -t order-service .` | |
| Run (docker) | `docker-compose up -d` | |

## Notable architecture facts
- Kafka publishes are fire-and-forget: publish failures do NOT fail the API call. On retry exhaustion events go to the `order.dlq` topic (consumed by `KafkaDlqListener`).
- Order number format: `ORD-{yyyyMMdd}-{sequence}`.
- Event topics: `order.created`, `order.status-changed`, `order.cancelled` (plus `order.dlq`).
- Kafka producer: `acks=all`, idempotent, 3 retries; StringSerializer (key) + JsonSerializer (value).

## Known Pitfalls (gotchas)
_(add entries here as they are discovered)_

## Architecture Decisions (ADRs)
_(add ADR entries here as they are made)_

## Lessons Learned
_(add lessons here)_

## Agent Memory Log
- **2026-08-06** — `memory-system`: Introduced the Repository Memory System. Updated `AGENTS.md`, `README.md` and `docs/memory.md` with the mandatory read/write flow; seeded this file with project facts.

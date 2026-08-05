# AGENTS.md — AI Agent Instructions

This file provides guidance for AI agents working on the Order Service codebase.

## Project Overview

- **Language**: Java 21
- **Framework**: Spring Boot 3.2
- **Build Tool**: Maven
- **Database**: PostgreSQL with Flyway
- **Messaging**: Apache Kafka (Spring Kafka)
- **Testing**: JUnit 5, Mockito, Testcontainers, MockMvc

## Directory Structure

```
order-service/
├── src/main/java/com/igorservice/orderservice/
│   ├── OrderServiceApplication.java      # Entry point
│   ├── config/                           # Security, Kafka, app config
│   ├── controller/                       # REST controllers
│   ├── dto/                              # Request/Response DTOs
│   ├── exception/                        # Custom exceptions, handler
│   ├── model/                            # JPA entities, enums
│   ├── repository/                       # Spring Data JPA repos
│   └── service/                          # Business logic, Kafka publisher
├── src/main/resources/
│   ├── application.yml                   # Config with profiles
│   └── db/migration/                     # Flyway SQL migrations
├── src/test/java/                        # Tests mirror main structure
├── Dockerfile
├── docker-compose.yml
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

## Key Design Decisions

- **Events are fire-and-forget**: Kafka publish failures do NOT fail API calls.
- **Order number format**: `ORD-{yyyyMMdd}-{sequence}`.
- **Status transitions are validated**: OrderService enforces valid state machine.
- **Soft delete**: Orders are never physically deleted; status → CANCELLED.

## Common Tasks

### Adding a new endpoint
1. Add DTO(s) in `dto/` if needed
2. Add method in `OrderService`
3. Add route in `OrderController`
4. Add validation in `GlobalExceptionHandler` if needed
5. Add unit test in `OrderServiceTest`
6. Add MockMvc test in `OrderControllerTest`

### Adding a new Kafka event
1. Define event type constant in publisher
2. Add publish method in `KafkaEventPublisher`
3. Call publisher from `OrderService` at appropriate state transition

### Database changes
1. Create Flyway migration `V{N}__description.sql` in `db/migration/`
2. Update entity model if needed
3. Update repository queries if needed

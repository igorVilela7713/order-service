# Repository Memory System

This page documents the **Repository Memory System** and the **mandatory read/write flow** every AI agent (Hermes, Claude, Copilot, etc.) must follow when working in `order-service`.

## Why a memory system?

`order-service` is a Spring Boot 3 microservice with Kafka, Flyway, PostgreSQL, and API-key auth. Its sharp edges — Flyway migration ordering, Kafka producer config (idempotence, `acks=all`), the fire-and-forget publish semantics, the `ORD-{yyyyMMdd}-{sequence}` numbering, Testcontainers startup quirks, and the JaCoCo 70% gate — are exactly the kind of detail that is expensive to rediscover per task. `MEMORY.md` is that living memory.

## Where it lives

- **Memory file:** [`MEMORY.md`](../MEMORY.md) at the repo root.
- **This doc:** `docs/memory.md`.
- **Agent contract:** `AGENTS.md` -> "Repository Memory System".

## The mandatory flow

### 1. READ (before any task)
Open `MEMORY.md` and read it in full before writing code, running a build, or opening a PR. Note the known pitfalls, reuse the verified commands, and honor recorded architecture decisions.

### 2. WRITE (after any task)
After completing a task (especially after fixing a bug, working around a pitfall, making an architecture decision, or validating a command), append to `## Agent Memory Log`:

```
- **YYYY-MM-DD** — `scope`: <one-line summary>.
  - **Learned:** <fact>.
  - **Where:** `<file>` + commit `<sha>` (or branch).
  - **Applies to:** <area/command/component>.
```

Newest entries go **first**.

## Commit policy
`MEMORY.md` must be committed — in its own atomic commit (`docs(memory): ...`) or with the task commit. Never leave it uncommitted.

## Repo quick reference

| Item | Detail |
|------|--------|
| Stack | Java 21; Spring Boot 3.2.5; PostgreSQL 16; Kafka 3.6+; Flyway; Spring Security (API key) |
| Build | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| Test | `./mvnw verify -Dspring.profiles.active=test` (JaCoCo enforces >= 70% coverage) |
| Deploy | `docker build -t order-service .` / `docker-compose up -d` |
| Key files | `OrderService.java`, `OrderController.java`, `OrderRepository.java`, `KafkaEventPublisher.java`, `OrderMetrics.java` |
| Notable | Kafka publishes are fire-and-forget; failures -> `order.dlq`. Order numbers: `ORD-{yyyyMMdd}-{sequence}` |

> The canonical, up-to-date version of every item lives in `MEMORY.md`. Treat this page as the explanation; treat `MEMORY.md` as the data.

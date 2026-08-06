# Known Errors, Bugs, and Pitfalls

> Issues discovered during development, with root causes and fixes applied.

---

## Git History of Fixes

The commit history shows a pattern of build/test issues encountered during development:

### Build & Dependency Issues

| Commit   | Message | Root Cause | Fix |
|----------|---------|------------|-----|
| `7b35bf2` | `fix(build): remove flyway-database-postgresql (not needed for Flyway 9.x)` | Flyway 9.x bundles PostgreSQL support — separate `flyway-database-postgresql` dependency caused classpath conflicts | Removed the unnecessary dependency |
| `e8cd1cf` | `fix(build): add explicit version to flyway-database-postgresql dependency` | Version mismatch when adding the dependency | Added explicit version (later reverted by removing it entirely) |
| `c867a01` | `fix(build): resolve compilation and runtime issues` | Multiple compilation and runtime issues during initial setup | Resolved compilation errors (details in PR #4) |
| `37bbc72` | `fix(auth): escape JSON string literal in ApiKeyAuthFilter response payload` | JSON string in `response.getWriter().write()` had unescaped quotes | Escaped JSON string literal properly |
| `1ade813` | `fix(docker): install curl for HEALTHCHECK in runtime stage` | `eclipse-temurin:21-jre` doesn't include `curl` | Added `apt-get install curl` in Dockerfile runtime stage |
| `ddb02dd` | `fix: remove duplicate Spring Retry and Logstash dependencies in pom.xml` | Duplicate dependency declarations | Cleaned up pom.xml |

### Test Issues

| Commit   | Message | Root Cause | Fix |
|----------|---------|------------|-----|
| `4084c5d` | `fix(test): use native query to override @CreationTimestamp in integration tests` | `@CreationTimestamp` on Order entity prevents tests from setting custom `createdAt` values via setters | Used native JPQL update query to bypass Hibernate timestamp generation |
| `d049ce1` | `fix(test): use repository findByCreatedAtBetween directly to avoid Hibernate timestamp override issues` | Same `@CreationTimestamp` issue affected date range search tests | Switched to using repository's `findByCreatedAtBetween` method directly |
| `65bf653` | `fix(test): add @Transactional to updateCreatedAt and fix search_byDateRange` | The native update query needed `@Transactional` annotation to work correctly | Added `@Transactional` to the repository method |
| `96df43b` | `fix(kafka): provide default broker for test profile placeholder` | `${spring.embedded.kafka.brokers}` placeholder not resolved in some test contexts | Added fallback default: `${spring.embedded.kafka.brokers:localhost:9092}` |
| `ae543dd` | `test: fix controller test context and logback config` | Controller tests failed due to missing context configuration | Fixed test context setup and logback configuration for test profile |
| `441ce49` | `fix(test): add @MockBean OrderSearchService to OrderControllerTest` | Controller test context was missing the `OrderSearchService` bean | Added `@MockBean OrderSearchService` to the test class |

---

## Known Bugs in Source Code

### TODO Comments Found

| File | Line | Comment |
|------|------|---------|
| `src/main/java/com/igorservice/orderservice/service/KafkaDlqListener.java` | 39 | `// TODO: Alerting / metrics / manual retry UI integration` |

This is the only TODO/FIXME in the codebase. It indicates the DLQ listener currently only logs failed events but does not:
- Send alerts (PagerDuty, Slack, email)
- Track DLQ message count as a metric
- Provide a UI for manual retry of failed events

---

## Build/Compilation Issues Encountered

### Flyway Dependency Conflict (PR #4)
- **Issue**: Adding `flyway-database-postgresql` dependency caused compilation errors
- **Why**: Flyway 9.x (bundled with Spring Boot 3.2.5) includes PostgreSQL support in `flyway-core`
- **Resolution**: Removed the unnecessary `flyway-database-postgresql` dependency entirely

### ApiKeyAuthFilter JSON Escaping (PR #4)
- **Issue**: `response.getWriter().write()` call in `ApiKeyAuthFilter` had unescaped JSON string literals
- **Why**: Java string literal contained `\"` that wasn't properly escaped
- **Resolution**: Escaped the JSON string literal in the response payload

---

## Test Issues and Pitfalls

### @CreationTimestamp Prevents Custom Timestamps in Tests
- **Issue**: `Order.createdAt` uses `@CreationTimestamp`, which means Hibernate ignores any value set via `order.setCreatedAt(...)` during persist
- **Impact**: Integration tests that need to create orders with specific `createdAt` values (for date range search tests) cannot do so through normal entity manipulation
- **Workaround**: The `OrderRepository.updateCreatedAt()` method uses a `@Modifying @Query` to bypass Hibernate and directly update the database column
- **Pitfall**: Any test that needs to control `createdAt` must use this repository method, not entity setters

### Test Profile Kafka Bootstrap Server
- **Issue**: `${spring.embedded.kafka.brokers}` is a Testcontainers/Spring Kafka placeholder that may not resolve in all contexts
- **Workaround**: Use `${spring.embedded.kafka.brokers:localhost:9092}` with a fallback default
- **Location**: `application.yml` test profile section

### Controller Test Context
- **Issue**: `OrderControllerTest` needs `@MockBean` for all injected dependencies, including `OrderSearchService`
- **Pitfall**: Forgetting to add `@MockBean OrderSearchService` causes `NoSuchBeanDefinitionException` at context startup

---

## CI Errors and How They Were Fixed

### CI Pipeline Structure (`.github/workflows/ci.yml`)
The CI has 3 jobs:
1. **build** — Compile + verify (unit + integration tests) + JaCoCo coverage + upload artifacts
2. **lint** — Compile + check for System.out/err usage + TODO/FIXME scanning
3. **docker** — Build Docker image + health check (only on main branch, depends on build job)

### Known CI Issues
- **JaCoCo coverage enforcement**: The `verify` phase includes JaCoCo check, but the CI step `Enforce minimum code coverage` only prints a message — it doesn't actually run `mvn jacoco:check` separately
- **Docker job health check retry loop**: Uses a retry loop (30 attempts × 2s = 60s timeout) to wait for application startup before health check
- **Test profile for CI**: `mvn -B verify -Dspring.profiles.active=test` — uses Testcontainers for PostgreSQL (no Docker service needed for DB, but CI does provision PostgreSQL via GitHub Actions service)

---

## Known Limitations and Gaps

### From PLAN.md — Pending Items

| Phase | Item | Status | Impact |
|-------|------|--------|--------|
| 3 | Event schemas (JSON Schema / Avro) | Not started | Events are raw JSON maps — no schema validation or evolution strategy |
| 6 | JaCoCo code coverage enforcement | In pom.xml but not fully enforced in CI | Build may pass with low coverage |
| 6 | OWASP security scanning | Not started | No automated vulnerability scanning |
| 6 | Kubernetes manifests (Helm) | Not started | No K8s deployment configuration |
| 7 | Redis caching for hot reads | Not started | All reads go to PostgreSQL |
| 7 | Order search by date range | Partially done (search endpoint exists) | May need performance optimization |
| 7 | Bulk operations | Not started | No batch create/update endpoints |
| 7 | Webhook notifications | Not started | No push notifications for order events |

### Architectural Gaps

1. **Order number sequence not persisted**: `AtomicLong` counter resets on JVM restart. For production, need database sequence or Redis-based counter.

2. **No event schema versioning**: Events are `Map<String, Object>` — no contract enforcement between producer and consumers.

3. **DLQ has no automated recovery**: Failed events are logged but require manual intervention. No retry-from-DLQ mechanism.

4. **No rate limiting**: No API rate limiting beyond Spring Security authentication.

5. **No request validation beyond annotations**: No custom business rule validation (e.g., customer existence check).

6. **Hardcoded CORS origins**: `localhost:3000` and `localhost:8080` — needs environment-specific configuration for production.

7. **API key is static**: No key rotation mechanism, no per-client keys.

---

## Error Handling Patterns in Code

### GlobalExceptionHandler (`exception/GlobalExceptionHandler.java`)
Handles 4 exception types:
- `OrderNotFoundException` → 404 Not Found
- `IllegalStateException` → 409 Conflict (invalid state transitions)
- `MethodArgumentNotValidException` → 422 Unprocessable Entity (validation errors with field details)
- `Exception` (catch-all) → 500 Internal Server Error

### OrderNotFoundException (`exception/OrderNotFoundException.java`)
- Extends `RuntimeException`
- Two constructors: message-only and message+cause
- Thrown by `OrderService` when order not found by ID

### Kafka DLQ Pattern (`service/KafkaDlqListener.java`)
- Listens on `order.dlq` topic
- Logs event details (eventType, orderId, orderNumber, customerId)
- Currently no automated action — manual investigation required
- TODO: Alerting / metrics integration

### Common Pitfalls for Developers

1. **@CreationTimestamp**: Don't try to set `createdAt` via entity setters in tests — use `OrderRepository.updateCreatedAt()` native query instead.

2. **Optimistic locking**: Concurrent updates to the same order will throw `OptimisticLockException`. Handle with retry or user-friendly error message.

3. **Status transitions**: Always go through `OrderService.updateOrderStatus()` or `cancelOrder()` — never set status directly on entity (state machine validation is in service layer).

4. **Kafka events are fire-and-forget**: Don't rely on event publishing succeeding — the API call returns before Kafka confirms delivery.

5. **Test context**: Controller tests need `@MockBean` for ALL service dependencies. Missing one causes context startup failure.

6. **Docker HEALTHCHECK**: The runtime image needs `curl` installed — it's not included in `eclipse-temurin:21-jre`.

7. **Flyway in test profile**: Disabled (`ddl-auto: create-drop`) — don't add migrations expecting them to run in tests.

8. **Order number uniqueness**: The `AtomicLong` counter is in-memory only. In clustered deployments, sequence numbers may collide. Use database sequence for production.

9. **JSON serialization**: Jackson configured with `write-dates-as-timestamps: false` — all Instant fields serialize as ISO 8601 strings, not epoch milliseconds.

10. **Spring Retry**: `@Retryable` methods must be called from outside the class (Spring proxy). Internal method calls bypass the retry interceptor.

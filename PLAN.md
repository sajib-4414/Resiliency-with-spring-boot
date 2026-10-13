# Microservices Learning Lab (Spring Boot): Order + Inventory, Sync first, Kafka later

## Context
Goal: learn distributed-systems basics step by step with two small Spring Boot services. **Phase 1 is only synchronous REST + resilience patterns.** Kafka and async failure scenarios come in later phases, built on the same two services. The working directory (`E:\Development practice\Claude Code\microserviceprojecttest`) is empty, so this is a greenfield build.

## Services (kept tiny on purpose)
- **order-service** (port 8081): the caller. `POST /orders` asks inventory-service whether stock is available, then saves the order.
- **inventory-service** (port 8082): the dependency. Owns stock levels and exposes `GET /inventory/{sku}`. Contains chaos switches to simulate slowness and failure.

```
client -> order-service --(REST: GET /inventory/{sku})--> inventory-service
```

Stack: Java 21, Spring Boot 3.x, Maven (two independent projects under one folder), Spring Web, Spring Data JPA + H2, Spring Boot Actuator, **Spring Cloud OpenFeign** (`spring-cloud-starter-openfeign`, with the Spring Cloud BOM version matching the Boot version) for the HTTP call, Resilience4j (`resilience4j-spring-boot3`) for resilience.

Feign + Resilience4j design: a declarative `@FeignClient` interface `InventoryClient`, which carries the Resilience4j annotations (`@Retry`, `@CircuitBreaker`, `@RateLimiter`, `@Bulkhead`) directly on its methods (no gateway class; Spring's AOP proxy around the Feign client applies them, verified in `InventoryRetryTest`). Fallbacks are handled by the exception handler. This keeps each pattern explicit and easy to toggle per experiment. Gotchas to handle: Spring Cloud's Feign Retryer is disabled by default (keep it that way so retries are only counted by Resilience4j), and a custom `ErrorDecoder` maps 404/409/429/5xx to distinct exceptions so only retryable errors are retried.

## Database
Database per service: no shared tables; order-service only learns stock via the REST call.
- **order-service**: `orders` (id, sku, qty, status, createdAt)
- **inventory-service**: `stock_item` (sku, available); later `processed_events` (eventId unique) for Kafka dedupe
- **Phase 1**: in-memory H2 for each service (`jdbc:h2:mem:...`), inventory seeded via `data.sql`, H2 console enabled for inspection.
- **Phase 2**: when Docker Compose is introduced for Kafka, switch each service to its own Postgres container (`orders_db`, `inventory_db`). Only the datasource config and driver dependency change; consider adding Flyway then.

## What "check stock, then save order" looks like
inventory-service data (seeded): `book` qty 10, `pen` qty 0, `laptop` qty 3.

```
client: POST /orders {"sku":"book","qty":2}
  order-service:
    1. GET inventory-service /inventory/book      -> {"sku":"book","available":10}   (sync call)
    2. available >= qty?   no  -> 422 "out of stock", nothing saved
                           sku unknown -> 404
                           yes -> save Order(sku=book, qty=2, status=CREATED)
    3. return 201
```
The caller needs the answer before it can respond, which is why this call is synchronous.

---

## Phase 1: Synchronous communication + resilience (DETAILED)

### Step 1.1: Plain sync call (no resilience)
- inventory-service: `StockItem` entity (sku, available), seeded via `data.sql`, `InventoryController` with `GET /inventory/{sku}`.
- order-service: `@EnableFeignClients`, `InventoryClient` (`@FeignClient(name="inventory", url="${inventory.url}")` with `@GetMapping("/inventory/{sku}")`), `OrderController` (calls `InventoryClient` directly; no gateway until Step 1.4) implementing the flow above, `Order` entity + repo.
- Verify the happy, out-of-stock, and unknown-sku paths with curl.

### Step 1.2: Chaos switches on inventory-service
`POST /chaos` with `{latencyMs, failureRate, down}` (in-memory flags applied by a filter on `/inventory/**`). This is the tool for every experiment below.

### Step 1.3: Timeouts (prerequisite for everything else)
Feign timeouts via `spring.cloud.openfeign.client.config.default.connectTimeout` / `readTimeout` (e.g. 1000ms / 2000ms) and Feign logger level `FULL` for visibility. Add the `ErrorDecoder`. Experiment: latency 10s with and without timeout; watch order-service requests hang vs fail fast.

### Step 1.4: Retry with exponential backoff
Resilience4j `@Retry`: `maxAttempts=4`, `waitDuration=500ms`, `enableExponentialBackoff`, `exponentialBackoffMultiplier=2`, optionally `enableRandomizedWait` (jitter). Retry only on IOException/5xx, not on 4xx (404/422 are real answers).
- Experiment: `failureRate=50%`, log attempt timestamps (~0.5s, 1s, 2s gaps). Then `down=true` to see exhaustion.
- Lesson: retry storms, why jitter, why only idempotent calls (this GET is safe to retry).

### Step 1.5: Circuit breaker
`@CircuitBreaker`: sliding window 10 calls, `failureRateThreshold=50`, `waitDurationInOpenState=10s`, `permittedNumberOfCallsInHalfOpenState=3`, `slowCallDurationThreshold`. Fallback: respond 503 "inventory unavailable, try later" (alternative to explore: accept the order as `PENDING_STOCK_CHECK`).
- Expose state via `/actuator/circuitbreakers` and `/actuator/health`.
- Experiment: `down=true` -> circuit opens, calls fail fast; clear chaos -> half-open -> closed.
- Combine with retry and observe how aspect order affects counting of failures.

### Step 1.6: Rate limiting
Two directions, both with Resilience4j `RateLimiter`:
- **Server side (inventory-service)**: limit `/inventory/**` to e.g. 5 req/s; excess returns **429 Too Many Requests** with `Retry-After`.
- **Client side (order-service)**: limit outbound calls so we don't overwhelm the dependency.
- Experiment: 50 parallel requests (PowerShell loop or `hey`); see 429s; make retry back off on 429 and decide whether the breaker counts 429 as a failure.

### Step 1.7 (optional): Bulkhead and a non-idempotent call
- `@Bulkhead` (max concurrent calls) so a slow dependency can't consume all request threads.
- Add `POST /inventory/{sku}/reserve` and see why blind retry is dangerous (double reservation). This sets up idempotency keys in a later phase.

### Phase 1 experiment table
| # | Setup | Observe | Lesson |
|---|-------|---------|--------|
| S1 | latency > timeout | Fast failure vs hanging requests | Timeouts |
| S2 | 50% failure | Retry masks flakiness, gaps double | Exponential backoff + jitter |
| S3 | service down | Circuit opens, fallback fires, half-open recovery | Circuit breaker |
| S4 | burst of requests | 429s, Retry-After | Rate limiting |
| S5 | slow + high concurrency | Thread exhaustion vs bulkhead rejection | Isolation |
| S6 | retry + breaker + limiter together | Interactions and tuning | Composition |
| S7 | retry on non-idempotent reserve | Double reservation | Idempotency |

### Key files (Phase 1)
- `inventory-service/`: `InventoryController`, `StockItem` (+repo), `ChaosController`, `ChaosFilter`, `application.yml` (rate limiter)
- `order-service/`: `OrderController`, `Order` (+repo), `InventoryClient` (Feign, Resilience4j annotations), `FeignErrorDecoder`, `InventoryClientConfig`, `application.yml` (retry/circuitbreaker/ratelimiter/bulkhead config), Actuator exposure
- `docs/experiments.md`: setup -> prediction -> result log

### Verification (Phase 1)
- Start both services; `curl -X POST localhost:8081/orders -H "Content-Type: application/json" -d '{"sku":"book","qty":2}'` returns 201; `pen` returns 422; `foo` returns 404
- Run each S1-S7 experiment via `POST localhost:8082/chaos` and check logs plus `/actuator/circuitbreakers`, `/actuator/retries`, `/actuator/ratelimiters`
- Optional: Resilience4j tests with WireMock for retry/breaker behavior

---

## Later phases (OUTLINE ONLY, to be detailed when we get there)
- **Phase 2: Kafka basics.** Docker Compose (KRaft + Kafka UI); order-service publishes `OrderPlaced`, inventory-service consumes it, decrements stock, and publishes an ack (`StockReserved` / `StockRejected`) back; order moves `PENDING -> CONFIRMED / REJECTED`.
- **Phase 3: Async failure modes.** Lost ack, duplicate events, idempotent consumer (`eventId` dedupe, no double stock decrement), crash before offset commit, out-of-order, poison pills, rebalances, replay.
- **Phase 4: Retry topics.** `@RetryableTopic` with exponential backoff (non-blocking retries), DLT + `@DltHandler`, comparison with the blocking `DefaultErrorHandler`, DLT replay.
- **Phase 5: Reliability patterns.** Transactional outbox, idempotency keys on `POST /orders`, producer idempotence/`acks=all`, ack timeouts, Saga with compensation (release stock if payment/order fails).
- **Stretch:** Testcontainers tests, Micrometer metrics, Avro + Schema Registry, Kafka transactions/exactly-once.

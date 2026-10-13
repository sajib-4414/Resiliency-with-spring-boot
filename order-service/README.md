# order-service

## Background

This is a learning project for trying out **resiliency in microservices**. The idea is to build two small Spring Boot services, `order-service` (this one) and `inventory-service`, and then deliberately break the communication between them to see how the system behaves and how to handle it.

The work is done in phases:

1. **Synchronous communication (current).** Order-service calls inventory-service over REST (Feign). We test error handling and resilience patterns: timeouts, retry with exponential backoff, circuit breaker, rate limiting and bulkhead. Inventory-service has chaos switches that inject latency and failures on demand.
2. **Asynchronous communication.** Introduce Kafka so the services exchange events instead of direct calls, including acknowledgements.
3. **Async failure modes.** Lost acknowledgements, duplicate events, idempotent consumers, out-of-order events, retry topics with backoff, and dead-letter topics.
4. **Reliability patterns.** Transactional outbox, idempotency keys and similar techniques.

The full plan is in `PLAN.md` in the repo root.

## What this service does

Takes orders over REST. Before saving an order it asks **inventory-service** (synchronously, via Feign) whether enough stock is available.

- Port: `8081`
- Stack: Java 21, Spring Boot 3.4, Spring Data JPA (in-memory H2), Spring Cloud OpenFeign, Resilience4j, springdoc (Swagger)
- Depends on: `inventory-service` at `http://localhost:8082` (`inventory.url` in `application.yml`)

## Run

Start `inventory-service` first, then run `OrderServiceApplication` from IntelliJ, or:

```
mvn spring-boot:run
```

## Tests

From the `order-service` folder:

```
mvn test
```

Run a single class or method:

```
mvn test -Dtest=OrderServiceTest
mvn test -Dtest=OrderControllerTest#returns422AndSavesNothingWhenOutOfStock
```

No other service needs to be running; inventory and the database are mocked. In IntelliJ, right-click `src/test/java` and choose **Run 'All Tests'**. (`inventory-service` has no tests yet.)

## API

| Method | Path | Description |
|---|---|---|
| POST | `/orders` | Create an order |
| GET | `/orders/{id}` | Get an order |

Swagger UI: http://localhost:8081/swagger-ui.html

Example:

```
POST /orders
{ "sku": "book", "qty": 2 }
```

## Seed data

Orders are checked against the stock that `inventory-service` seeds on startup (from its `data.sql`; see `inventory-service/README.md`):

| sku | available | Order of qty 1 |
|---|---|---|
| `book` | 10 | 201 (up to qty 10) |
| `pen` | 0 | 422 out of stock |
| `laptop` | 3 | 201 (up to qty 3) |

Any other sku gives 404. Order-service's own database starts empty. Stock is not reduced when an order is created.

## Status codes

| Code | When |
|---|---|
| 201 | Order created |
| 400 | Malformed body, wrong type, or `sku`/`qty` missing |
| 404 | Unknown sku, or order id not found |
| 422 | `sku` blank, `qty` < 1, or not enough stock |
| 503 | Inventory is down or returned a 5xx |
| 504 | Inventory did not respond in time |

Error body: `{"status": "422", "error": "..."}`. The full rules are in the root `CLAUDE.md`.

## Database

In-memory H2, reset on every restart. Console: http://localhost:8081/h2-console
JDBC URL `jdbc:h2:mem:orders_db`, user `sa`, empty password.

## Calls to inventory

Feign client with a 1s connect timeout and 2s read timeout (`application.yml`). Retry, circuit breaker, rate limiting and bulkhead are added step by step; see `PLAN.md`.

# inventory-service

## Background

This is a learning project for trying out **resiliency in microservices**. The idea is to build two small Spring Boot services, `order-service` and `inventory-service` (this one), and then deliberately break the communication between them to see how the system behaves and how to handle it.

This service is the **dependency**: the one that gets slow, fails or goes down so that `order-service` has something to survive. It has chaos switches that inject latency and failures on demand.

The work is done in phases:

1. **Synchronous communication (current).** Order-service calls inventory-service over REST (Feign). We test error handling and resilience patterns: timeouts, retry with exponential backoff, circuit breaker, rate limiting and bulkhead.
2. **Asynchronous communication.** Introduce Kafka so the services exchange events instead of direct calls, including acknowledgements.
3. **Async failure modes.** Lost acknowledgements, duplicate events, idempotent consumers, out-of-order events, retry topics with backoff, and dead-letter topics.
4. **Reliability patterns.** Transactional outbox, idempotency keys and similar techniques.

The full plan is in `PLAN.md` in the repo root.

## What this service does

Owns stock levels and tells callers how many units of a sku are available.

- Port: `8082`
- Stack: Java 21, Spring Boot 3.4, Spring Data JPA (in-memory H2)
- Called by: `order-service` (`GET /inventory/{sku}`)

## Run

```
mvn spring-boot:run
```

Or run `InventoryServiceApplication` from IntelliJ. Start this service before `order-service`.

## API

| Method | Path | Description |
|---|---|---|
| GET | `/inventory/{sku}` | Stock for a sku: `{"sku": "book", "available": 10}`; 404 if the sku is unknown |
| POST | `/chaos` | Set chaos switches (lab only, off by default) |
| GET | `/chaos` | Show the current chaos switches |

## Seed data

The database starts with these rows every time the service starts:

| sku | available |
|---|---|
| `book` | 10 |
| `pen` | 0 |
| `laptop` | 3 |

Any other sku returns 404. Stock changes are not persisted; a restart resets everything to the table above.

## How the data is seeded

1. **Schema:** Hibernate creates the `stock_item` table from the `StockItem` entity (`spring.jpa.hibernate.ddl-auto: create-drop`). The table is dropped on shutdown.
2. **Data:** Spring Boot runs `src/main/resources/data.sql` at startup, which contains one `INSERT INTO stock_item ...` per row above.
3. **Ordering:** `spring.jpa.defer-datasource-initialization: true` makes `data.sql` wait until Hibernate has created the table. Without it the inserts fail because the table does not exist yet.
4. **Always run:** `spring.sql.init.mode: always` forces the script to run. By default Boot only does so for embedded databases.

To change the seed data, edit `data.sql` and restart. Keep the seed table in this README, and in `order-service/README.md`, in sync with it.

## Database

In-memory H2, reset on every restart. Console: http://localhost:8082/h2-console
JDBC URL `jdbc:h2:mem:inventory_db`, user `sa`, empty password.

## Chaos switches

A filter applies these to `/inventory/**` only:

```
POST /chaos
{ "latencyMs": 3000, "failureRate": 0.5, "down": false }
```

| Field | Effect |
|---|---|
| `latencyMs` | Sleep this long before answering |
| `failureRate` | Fraction of requests (0 to 1) that fail with 500 |
| `down` | Every request fails with 503 |

Send `{}` to reset everything to normal.

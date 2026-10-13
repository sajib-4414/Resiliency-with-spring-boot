# Ground Rules

Learning lab: `order-service` (8081) calls `inventory-service` (8082). See `PLAN.md` for the phased plan. Phase 1 = sync REST + Resilience4j; Kafka comes later.

## Version control (mandatory)
- Everything lives in one git repo at the project root. Never work without it.
- Commit after every completed step of `PLAN.md` (e.g. "Step 1.4: retry with exponential backoff"). Small, working commits; the build must pass before committing.
- Commit message: short imperative subject (`Add retry with exponential backoff`), optional body explaining why. End with the Co-Authored-By trailer when Claude wrote the change.
- Work on `main` for now; use a branch (`phase-2-kafka`, `step-1.5-circuit-breaker`) for anything risky or experimental, merge when it works.
- Never commit `target/`, IDE files, logs or secrets (see `.gitignore`). Never commit passwords; use env vars (`${DB_PASSWORD}`) once a real DB arrives.
- Do not push, force-push, rewrite history or delete branches unless asked.
- Update `PLAN.md` when the plan changes, in the same commit as the code.

## HTTP status codes (use the most specific meaningful code)
| Code | Use when | Example here |
|---|---|---|
| 200 OK | Successful read/update with a body | `GET /orders/{id}` |
| 201 Created | A resource was created | `POST /orders` |
| 204 No Content | Success, nothing to return | future deletes |
| 400 Bad Request | Request is malformed: unparseable, wrong type, or a required field missing/null | invalid JSON, `"qty": "abc"`, no `sku` |
| 401 / 403 | Not authenticated / not allowed | future |
| 404 Not Found | The resource does not exist | unknown sku, unknown order id |
| 409 Conflict | Request conflicts with current state, e.g. duplicate or version clash | duplicate idempotency key (later) |
| 422 Unprocessable Entity | Well-formed and present, but the value breaks a rule (validation or business) | `qty` < 1, blank `sku`, not enough stock |
| 429 Too Many Requests | Rate limited; include `Retry-After` | inventory rate limiter |
| 500 Internal Server Error | Our own unexpected bug | uncaught exception |
| 502 Bad Gateway | Dependency returned an invalid response | malformed reply from inventory |
| 503 Service Unavailable | Dependency down or circuit open; may succeed later | inventory down, circuit open, bulkhead full |
| 504 Gateway Timeout | Dependency did not answer in time | Feign read/connect timeout |

Rules:
- Pick 4xx when the caller can fix the request, 5xx when the fault is ours or a dependency's.
- Never return 200 with an error inside the body. Never use 500 for a known, expected condition.
- Error bodies are JSON: `{"status": <code>, "error": "<message>"}`. Don't leak stack traces or internals.
- Only retry on 5xx/timeouts (and 429 with backoff). Never retry 400/404/422.

## Code conventions
- Package per service: `com.example.order`, `com.example.inventory`. Match the surrounding style; keep it small and readable.
- Layering: Controller -> Service -> Repository/Client. Controllers only handle HTTP (binding, validation, status codes) and delegate to a `@Service` class (`OrderService`, `InventoryService`); no business logic, repository or client calls in controllers. Services hold business rules, take plain values (not web DTOs) and throw domain exceptions that `ApiExceptionHandler` maps to status codes (not `ResponseStatusException`). Every new feature gets a service method plus a plain Mockito unit test for it.
- Resilience4j annotations live on the Feign client methods in `InventoryClient` (no gateway class), not the controller or service. Config goes in `application.yml`.
- Chaos switches (`/chaos`) are lab-only scaffolding; they must stay off by default.
- Each experiment gets a note in `docs/experiments.md`: setup, prediction, result.

## Running
- Services run from IntelliJ or `java -jar <service>/target/*.jar`. In-memory H2 (user `sa`, empty password); consoles at `/h2-console`.
- Machine memory is limited: run the services with `-Xmx256m` if needed, and don't start background processes without asking.

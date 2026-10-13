# Experiments

Each experiment: setup, prediction (written BEFORE running), result.

Switch chaos on/off (inventory-service, port 8082; it stays on until reset):

```
POST http://localhost:8082/chaos   {"failureRate":0.5}
POST http://localhost:8082/chaos   {}          <- reset to normal
```

Fire a request: `POST http://localhost:8081/orders  {"sku":"book","qty":1}`

## E1: Baseline, 50% failures, no retry (before Step 1.4)

- Setup: `failureRate=0.5`, 20 orders for `book`, retry disabled (checkout of the commit before Step 1.4, or `maxAttempts: 1`).
- Prediction: about 10 of 20 return 503, the rest 201.
- Result: _to fill in_

## E2: 50% failures with retry (Step 1.4)

- Setup: `failureRate=0.5`, 20 orders; retry on (4 attempts, 0.5s / 1s / 2s waits).
- Prediction: 0 to 2 return 503 (each request fails only if 4 attempts in a row fail, about 6%); Feign DEBUG log shows repeated `GET /inventory/book` with growing gaps.
- Result: _to fill in_

## E3: Inventory down, retry gives up (Step 1.4)

- Setup: `down=true`, 1 order.
- Prediction: 4 attempts then 503 with JSON body, taking about 3.5s (0.5 + 1 + 2 waits).
- Result: _to fill in_

## E4: Inventory down, circuit breaker (Step 1.5)

- Setup: all chaos off, 1 baseline order, then `down=true` and 4 orders in a row; then `down=false` and keep ordering while polling `/actuator/circuitbreakers`.
- Prediction: the first failing order takes ~3.5s (4 attempts) and opens the circuit; the next orders return 503 in a few ms without reaching inventory; after 10s the circuit goes half-open, and 3 good trial calls close it.
- Result: baseline 201. With `down=true`, order 1 returned 503 in 3.57s and the state became OPEN (1 success + 4 failed attempts = 5 calls, 80% failure, so the minimum of 5 calls was reached at once). Orders 2-4 returned 503 in 11-31ms. The state was already HALF_OPEN by the time chaos was cleared (automatic transition after 10s). The next 3 orders returned 201 and the state became CLOSED; later orders stayed 201.
- Lessons: retry attempts count toward the window, so retry makes the breaker trip faster; the breaker turns a 3.5s failure into a ~20ms one. Not exercised: a failed half-open trial reopening the circuit.

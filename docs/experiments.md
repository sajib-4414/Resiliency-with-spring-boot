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

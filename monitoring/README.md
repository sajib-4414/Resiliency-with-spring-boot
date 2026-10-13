# Monitoring (Prometheus + Grafana)

Both services expose metrics at `/actuator/prometheus` (Micrometer). Prometheus scrapes them every 5s; Grafana charts them.

## Start

1. Start Docker Desktop and wait until it says it is running.
2. Start inventory-service, then order-service (they run on the host, not in Docker).
3. From this folder:

```
docker compose up -d
```

4. Check targets are UP: http://localhost:9090/targets
5. Open Grafana: http://localhost:3000 (user `admin`, password `admin`, skip the password change). Dashboard: **Resilience lab**.

Stop with `docker compose down`.

## Dashboard panels

| Panel | Query idea | Reads as |
|---|---|---|
| Orders by retry outcome | `resilience4j_retry_calls_total` by `kind` | how many orders needed a retry, succeeded or gave up |
| Attempts vs orders | inventory `http_server_requests_seconds_count` vs order POST count | attempts above orders = retries (retry amplification) |
| Order responses by status | order-service `http_server_requests_seconds_count` by `status` | 201 vs 503 vs 504 |
| Slowest order request | `http_server_requests_seconds_max` | what retries cost in latency |

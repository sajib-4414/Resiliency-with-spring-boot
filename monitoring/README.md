# Monitoring (Prometheus + Grafana)

Both services expose metrics at `/actuator/prometheus` (Micrometer). Prometheus scrapes them every 5s; Grafana charts them.

## Run on Windows (no Docker)

1. Download the Windows zips: Prometheus (prometheus.io/download, `windows-amd64`) and Grafana OSS (grafana.com/grafana/download, Windows standalone zip). Unzip each somewhere, for example `C:\tools\prometheus` and `C:\tools\grafana`.
2. Start inventory-service, then order-service. Check `http://localhost:8081/actuator/prometheus` shows text metrics.
3. **Prometheus**, in its own terminal:
   ```
   C:\tools\prometheus\prometheus.exe --config.file="E:\Development practice\Claude Code\microserviceprojecttest\monitoring\windows\prometheus.yml"
   ```
   Check http://localhost:9090/targets: both targets **UP**.
4. **Grafana data source and dashboard**: copy two files into Grafana's provisioning folders (create the folders if missing), then start it:
   - `monitoring\windows\grafana-datasource.yml` -> `C:\tools\grafana\conf\provisioning\datasources\`
   - `monitoring\windows\grafana-dashboards.yml` -> `C:\tools\grafana\conf\provisioning\dashboards\`
   ```
   C:\tools\grafana\bin\grafana.exe server --homepath C:\tools\grafana
   ```
5. Open http://localhost:3000 (user `admin`, password `admin`, skip the change). Dashboard: **Resilience lab**.

Stop each one with Ctrl+C in its terminal.

## Run with Docker (alternative)

Start Docker Desktop, then `docker compose up -d` in this folder. Uses the files in this folder and `grafana\`, not `windows\`.

## Dashboard panels

| Panel | Query idea | Reads as |
|---|---|---|
| Orders by retry outcome | `resilience4j_retry_calls_total` by `kind` | how many orders needed a retry, succeeded or gave up |
| Attempts vs orders | inventory `http_server_requests_seconds_count` vs order POST count | attempts above orders = retries (retry amplification) |
| Order responses by status | order-service `http_server_requests_seconds_count` by `status` | 201 vs 503 vs 504 |
| Slowest order request | `http_server_requests_seconds_max` | what retries cost in latency |

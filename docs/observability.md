# Observability

Micrometer -> `/actuator/prometheus` -> Prometheus (scrape 5 s) -> Grafana dashboard *FleetFlow Overview* (provisioned).

Custom metrics: `orders_created_total`, `orders_completed_total`, `orders_failed_total`, `dispatch_attempts_total`, `dispatch_success_total`, `dispatch_latency_seconds`, `driver_assignment_latency_seconds`, `events_processed_total`, `events_failed_total`, `dlq_messages_total`, `orders_unassigned` (gauge). Built-in: `http_server_requests_seconds` (API latency/errors), Hikari, JVM, Kafka consumer lag (`kafka_consumer_fetch_manager_records_lag_max`), Redis via `redis_exporter`.
Also: `redis_cache_hits_total` / `redis_cache_misses_total` (tag `cache`), `retention_deleted_total` (tag `table`). System Health (`/api/v1/system/health`) reports Database, Redis, Kafka (broker reachability), Event pipeline (outbox backlog) and Dispatch (consumer containers running); Kafka and Dispatch show "Not configured" with the log publisher.
Verify metric names in Prometheus before trusting a panel; I have not run the stack.

Correlation: every request gets `X-Correlation-Id` (or a generated one) placed in the log MDC and copied into each event envelope; consumers restore it, so one order can be traced across API, outbox, Kafka and dispatch logs. Add `%X{correlationId}` to the log pattern to see it.
`/actuator/prometheus` is unauthenticated for scraping: expose it only on an internal network.

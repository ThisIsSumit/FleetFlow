# Failure handling and simulation

Enable with `DEV_TOOLS=true` (OWNER only): `POST /api/v1/dev/faults {fault, enabled, param}`, `GET /api/v1/dev/faults`, `POST /api/v1/dev/actions/duplicate-last-event`, `POST /api/v1/dev/actions/disconnect-driver/{id}`.

**These are the expected behaviours by design. None has been executed and recorded yet; run each and write down what you observe.**

| Experiment | How | Expected |
|---|---|---|
| Kafka unavailable | `KAFKA_UNAVAILABLE` on, create orders | requests still succeed (outbox rows PENDING); relay retries with backoff; disable and rows drain to SENT |
| Redis unavailable | `REDIS_UNAVAILABLE` on | assignment falls back to DB row lock; nearby search fails so dispatch retries via sweeper; rate limiter fails open |
| Database timeout | `DB_TIMEOUT` on | assignment throws, consumer retries 3x, then DLQ; replay after disabling |
| Consumer crash | `CONSUMER_CRASH` param N | next N deliveries fail and retry, then succeed; no duplicate assignments |
| Slow consumer | `SLOW_CONSUMER` param ms | consumer lag rises in Grafana; drains when disabled |
| Duplicate event | duplicate-last-event | second delivery logged as duplicate, no second effect |
| Driver disconnect | disconnect-driver | driver goes OFFLINE through the normal service path: removed from GEO, `driver.unavailable` emitted, waiting (ASSIGNED) orders return to dispatch and go to another driver; in-progress orders raise an alert (covered by `DispatchFlowIT`) |
| Driver stops pinging | stop the driver app | after 120 s the driver is skipped by dispatch; after 900 s evicted from the map (`GeoFreshnessIT`) |
| Dispatch failure | `DISPATCH_FAILURE` on | retries then DLQ; replay from `/admin/dead-letters` |

# Scaling notes

- **API:** stateless (JWT), scale horizontally. WebSocket fan-out uses Redis pub/sub, so any instance can serve any socket.
- **Outbox relay:** `FOR UPDATE SKIP LOCKED` lets many instances poll concurrently. Bottleneck to measure: one transaction per batch while publishing synchronously.
- **Consumers:** parallelism = partitions (3 per topic today); increase partitions before adding consumers. Ordering is per key.
- **Assignment:** contention is per driver, so unrelated assignments run in parallel; hot drivers serialise on the row lock.
- **Postgres:** indexes exist for tenant+time, tenant+status and outbox pending. Dashboard aggregates are live queries; add caching or materialised views if they show up in profiles.
- **Redis:** single node in dev; GEO per org keeps sets small.
- **Unmeasured:** no throughput or latency numbers exist. Run `load-tests/create-orders.js` at 100/500/1000 rps and record results in the README.

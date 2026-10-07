# Dispatch engine

1. **Trigger:** `dispatch.requested` (from the outbox).
2. **Search:** Redis `GEOSEARCH` within the org's radius (default 5 km), nearest first, max 50.
3. **Filter:** same organization, status `AVAILABLE`, ACTIVE vehicle at least as large as required, active-order count below the workload limit.
4. **Score:** `0.40*distance + 0.20*availability + 0.15*workload + 0.15*vehicleMatch + 0.10*rating` (weights per organization, normalised). Deterministic, not machine learning. Straight-line distance only.
5. **Freshness:** only drivers with a location ping in the last 120 s are candidates (Redis `geo:seen`).
6. **Assign atomically** in rank order (see below). First success wins.
6. **No candidate:** the order stays `DISPATCH_REQUESTED`; a sweeper re-emits the event every 15 s once it is 30 s old After `fleetflow.dispatch.max-attempts` (default 20, about 10 minutes) the order is moved to FAILED, a `DELIVERY_FAILED` event is emitted and a SYSTEM_ALERT notification is requested. A dispatcher's "retry" resets the budget. Each order is swept in its own transaction so one conflict cannot roll back the rest. Dispatchers can also assign manually from the Dispatch page.

## Concurrency
Redis lock `lock:driver:{id}` (SET NX PX, token-checked release) queues contenders; then, in a `REQUIRES_NEW` transaction: `SELECT ... FOR UPDATE` on the driver, workload counted under that lock, order `@Version` guards double assignment. The transaction commits before the lock is released. Redis down = DB-only path (slower, still correct). Test: `AssignmentConcurrencyIT`.

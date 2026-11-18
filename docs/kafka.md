# Kafka

Topics (3 partitions, 1 replica in dev): `order.created`, `dispatch.requested`, `driver.assigned`, `driver.location.updated`, `delivery.started`, `delivery.completed`, `delivery.cancelled`, `driver.unavailable`, `notification.requested`. DLQs: `<topic>.DLQ` for every consumed topic (`order.created`, `dispatch.requested`, `driver.unavailable`, `notification.requested`, `driver.assigned`, `delivery.completed`, `delivery.cancelled`). Consumer logic lives in `EventHandlers`; the Kafka listeners only delegate. Without Kafka (`EVENTS_PUBLISHER=log`) the same handlers run in-process.

- **Keys:** `orderId` for order lifecycle events (per-order ordering), `driverId` for location events. `driver.unavailable` is emitted (via the outbox) when a driver goes OFFLINE or ON_BREAK; its consumer returns not-yet-picked-up orders to dispatch and alerts on in-progress ones. `notification.requested` is how every notification is created: producers emit it through the outbox and a consumer writes the `notifications` row. Notifications therefore require `EVENTS_PUBLISHER=kafka`. DLQs exist for `order.created`, `dispatch.requested`, `driver.unavailable` and `notification.requested`.
- **Envelope:** `eventId, eventType, organizationId, aggregateId, correlationId, timestamp, payload`.
- **Producing:** only through the transactional outbox (except location telemetry, which is best-effort). Producer `acks=all`, idempotent.
- **Consuming:** at-least-once; consumers dedupe with `processed_events`.
- **Retry/DLQ:** 3 retries, backoff 1 s/2 s/4 s, then `<topic>.DLQ`; malformed events skip retries. DLQ messages are stored in `dead_letter_events` and can be listed/replayed via `/api/v1/admin/dead-letters`.
- **Trade-off:** retries block the partition (keeps ordering, simple) instead of retry topics.

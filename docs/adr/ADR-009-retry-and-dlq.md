# ADR: Retry and DLQ strategy

## Context
Consumer failures are usually transient (DB blip) but some events are poison.

## Decision
Spring Kafka DefaultErrorHandler: 3 retries with exponential backoff (1s, 2s, 4s), then publish to `<topic>.DLQ`. Malformed events skip retries. A recorder persists DLQ messages to `dead_letter_events`; `GET /api/v1/admin/dead-letters` lists them and `POST /{id}/replay` re-publishes the original payload (same eventId) to its original topic.

## Alternatives
Non-blocking retry topics (better throughput, but breaks per-order ordering and adds topics); infinite retry (blocks a partition forever); drop on failure (loses data).

## Trade-offs
Blocking retries stall the partition for about 7 seconds per failing event. Acceptable at this scale; revisit with retry topics if lag under load testing shows it matters. Replay safety depends on idempotent consumers.

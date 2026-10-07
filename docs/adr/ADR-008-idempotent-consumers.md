# ADR: Idempotent consumers

## Context
The outbox relay and Kafka are at-least-once, so the same event can arrive twice.

## Decision
Table `processed_events(consumer, event_id)` with a composite primary key. The order-created handler inserts the ledger row and applies its effect in one transaction, so a failure rolls back both. The dispatch handler checks the ledger first and marks it only after success, because its assignment runs in its own transaction; assignment is itself idempotent through order-status checks.

## Alternatives
Relying on Kafka exactly-once (does not cover the DB side effects); natural idempotency only (harder to prove); Redis SETNX dedupe (loses state on flush).

## Trade-offs
The ledger grows without bound and needs a retention job (not built yet). A crash between effect and mark in the dispatch handler causes one harmless re-run.

# ADR: Transactional outbox

## Context
Writing to PostgreSQL and Kafka in one request is an unsafe dual-write: either can succeed alone.

## Decision
Write the state change and an `outbox_events` row in one transaction; a relay publishes with `SKIP LOCKED`, retries with exponential backoff and marks rows FAILED after 10 attempts.

## Alternatives
Publish after commit (can lose events on crash); Kafka transactions across DB (not possible); CDC with Debezium (more infrastructure).

## Trade-offs
At-least-once delivery, added latency (poll interval) and an outbox table to prune (no pruning job yet). Consumers must be idempotent.

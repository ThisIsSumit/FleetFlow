# ADR: PostgreSQL as source of truth

## Context
Several stores (Redis, Kafka) hold copies of state; one must win.

## Decision
PostgreSQL owns orders, drivers, assignments, tokens, ledgers. Redis is rebuildable (GEO, locks, counters); Kafka carries committed facts. Correctness never depends on Redis or Kafka being up.

## Alternatives
Redis as primary for live state; event sourcing in Kafka.

## Trade-offs
Every correctness guarantee sits in one relational store (row locks, unique keys, transactions), at the cost of more load on Postgres for hot paths.

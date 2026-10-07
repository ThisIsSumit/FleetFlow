# ADR: Driver assignment concurrency control

## Context
Two orders can pick the same driver at the same instant. Only `max_workload` assignments may succeed.

## Decision
Two layers. (1) A Redis lock `lock:driver:{id}` (SET NX PX 10s, token-checked Lua release) queues contenders cheaply. (2) Inside a REQUIRES_NEW transaction the driver row is locked with SELECT FOR UPDATE, the active-order count is checked under that lock, and the order's @Version guards against two drivers being assigned to one order. The transaction commits before the Redis lock is released. If Redis is down the code logs and relies on layer 2 alone.

## Alternatives
Redis lock only (unsafe if the lock expires mid-work); DB lock only (correct but contention lands on Postgres); a unique index on active orders per driver (rejected because workload is configurable above 1); Redlock across nodes (unnecessary with a single Redis and a DB backstop).

## Trade-offs
Redis is an optimisation, not the source of correctness, so an outage costs throughput rather than integrity. The cost is two mechanisms to reason about, and a lock holder that stalls past the TTL can be overtaken (the DB layer still prevents a double assignment).

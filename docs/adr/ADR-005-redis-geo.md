# ADR: Redis GEO

## Context
Dispatch must find drivers near a pickup point many times per second, while drivers move constantly. Postgres would take a write per location ping.

## Decision
Keep live driver positions in Redis GEO, one key per organization (`geo:drivers:{orgId}`), queried with GEOSEARCH (radius, sorted by distance, limit 50). PostgreSQL stays the source of truth for driver identity, status and assignments; the GEO set is a disposable index.

## Alternatives
PostGIS (accurate, transactional, but write-heavy for pings); in-memory grid in the app (not shared across instances); geohash columns in Postgres.

## Trade-offs
Per-org keys give tenant isolation for free but many small keys. Positions are lost if Redis is flushed until drivers next ping. No staleness expiry yet (a driver who stops pinging stays indexed until set OFFLINE), which is a known gap.

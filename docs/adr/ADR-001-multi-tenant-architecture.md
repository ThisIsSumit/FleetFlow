# ADR: Multi-tenant architecture

## Context
A B2B SaaS must isolate organizations while staying cheap to operate.

## Decision
Shared database and schema with an `organization_id` column on every business table. The tenant comes only from the JWT; all reads/writes go through tenant-scoped repository methods.

## Alternatives
Database per tenant (strong isolation, costly to migrate/operate); schema per tenant; PostgreSQL row-level security only.

## Trade-offs
Simple and cheap, but isolation depends on discipline in every query. Mitigations: tests per tenant boundary, 404-not-403 for foreign ids. Row-level security was added as a second layer (ADR-011).

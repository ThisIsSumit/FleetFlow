# ADR-011: PostgreSQL row-level security

## Context
Tenant isolation depended on every query carrying an `organization_id` filter. One forgotten filter would leak data.

## Decision
Enable and FORCE row-level security on all tenant tables with one policy, `app_tenant_ok(organization_id)`. A DataSource wrapper sets `app.current_org` for authenticated requests and `app.bypass_rls=on` for system work with no user. The application connects as a non-superuser role; Flyway uses the owner role.

## Alternatives
Application filters only (the previous state); Hibernate `@Filter`/multi-tenancy (still application-side); schema or database per tenant (operationally heavy).

## Trade-offs
One extra round trip per connection checkout. Superusers bypass RLS, so a misconfigured deployment silently loses the protection (mitigated by the compose setup and `RlsIT`). System threads run in bypass mode and must be trusted code. Policies must be added for every new tenant table (the migration loops over a list).

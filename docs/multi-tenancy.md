# Multi-tenancy

Shared database, shared schema; every business row carries `organization_id`.

- The tenant is read from the verified JWT (`TenantContext`), never from a request body or parameter.
- Repositories expose tenant-scoped finders (`findByIdAndOrganizationId`); another tenant's id behaves like a missing row (404), so existence is not leaked.
- Redis keys embed the organization (`geo:drivers:{org}`) and WebSocket fan-out is per organization channel (`live:{org}`).
- System jobs (outbox relay, sweeper, delayed-delivery scanner) are cross-tenant by design and only move data forward; they never return data to a caller.
- Tests: cross-tenant order read/update, vehicle attach, dashboard counts and team listing (`FleetFlowIT`, `AuthFlowIT`, `AccountFlowIT`, `DispatchFlowIT`).
- **PostgreSQL row-level security (defence in depth, ADR-011):** every tenant table has a policy `app_tenant_ok(organization_id)`. On each connection checkout `TenantAwareDataSource` sets `app.current_org` (authenticated request) or `app.bypass_rls=on` (no user: Kafka consumers, schedulers, login/signup, Flyway). Unset variables mean no rows are visible. A forgotten filter in an authenticated request therefore cannot leak another tenant's rows, and cross-tenant writes are rejected by the database.
- RLS only applies to a **non-superuser** role. Compose creates `fleetflow_app` (see `infrastructure/postgres/01-app-role.sh`) and runs Flyway as the owner. Running the backend locally as the Postgres superuser silently disables RLS. `RlsIT` proves the policies with a non-superuser role.
- Limitations: WebSocket and system threads run in bypass mode (their tenant comes from the token or event, not the DB); the email-uniqueness check on invite only sees the caller's tenant, so a clash with another tenant surfaces when the invitation is accepted.

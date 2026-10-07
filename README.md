# FleetFlow

**Intelligent dispatch infrastructure for modern delivery operations.**

## What is FleetFlow?
A multi-tenant B2B SaaS for delivery companies: orders, drivers, vehicles, automatic driver dispatch, live tracking, notifications and analytics, on an event-driven backend (Spring Boot, Kafka, Redis, PostgreSQL) with a React dashboard.

## Problem
Delivery teams must continuously match incoming orders to drivers by location, availability, workload, vehicle type and priority, while drivers move, go offline, and several orders compete for the same driver. FleetFlow automates this deterministically, keeps the data consistent under concurrency and failures, and makes it observable.

## Features
- Organizations with isolated data; roles OWNER / ADMIN / DISPATCHER / DRIVER (role-based API authorization).
- Driver app (mobile-first, `/driver`): status, assigned deliveries, GPS or simulated location sharing; see [docs/driver-app.md](docs/driver-app.md).
- Auth: signup, login, 15-min access tokens, rotating refresh cookie with reuse detection, logout / logout-all, session list and revoke, email verification, password reset, team invitations (SMTP email with branded HTML; Mailpit locally).
- Orders (filters, sort, paging, timeline with actor and event id), drivers, vehicles, driver detail.
- Automatic dispatch + manual dispatch console; configurable radius, workload and scoring weights per organization.
- Live map (Leaflet + OpenStreetMap tiles, clustering) fed by WebSocket location updates; notification centre; analytics; dashboard with system health.
- Redis rate limiting and read-through cache, address search (geocoder), PostgreSQL row-level security, scheduled retention of append-only tables, Prometheus metrics, provisioned Grafana dashboard, failure-simulation API, demo seed data, k6 load-test script, CI workflow.

## Architecture
See [docs/architecture.md](docs/architecture.md). Summary: React -> Spring Boot -> (PostgreSQL source of truth, Redis for GEO/locks/rate limits/pub-sub, Kafka via transactional outbox) -> idempotent consumers. Deep dives: [authentication](docs/authentication.md), [multi-tenancy](docs/multi-tenancy.md), [dispatch engine](docs/dispatch-engine.md), [kafka](docs/kafka.md), [redis](docs/redis.md), [failure handling](docs/failure-handling.md), [observability](docs/observability.md), [scaling](docs/scaling.md), and ADRs 001-010 in [docs/adr](docs/adr).

## Tech stack
Backend: Java 21, Spring Boot 3.3 (Web, Security, Data JPA, WebSocket, Kafka, Data Redis, Actuator), Flyway, PostgreSQL 16, Kafka (KRaft), Redis 7, Micrometer, springdoc OpenAPI, JUnit 5, Testcontainers.
Frontend: React 18, Vite, TypeScript, React Router, TanStack Query, Zustand (auth/theme/toasts only), Tailwind, React Hook Form + Zod, Recharts, Leaflet, Axios. A custom component set is used instead of shadcn/ui.
Infra: Docker Compose, Prometheus, Grafana, redis_exporter, GitHub Actions.

## Authentication
Access JWT in memory; refresh token as HttpOnly SameSite=Strict cookie, stored hashed, rotated on every use; replaying a rotated token revokes all sessions. Details and known limits in [docs/authentication.md](docs/authentication.md).

## Multi-tenancy
Shared schema with `organization_id` everywhere; tenant derived from the JWT only; foreign ids return 404. Row-level security is enabled as a second layer for a non-superuser app role (ADR-011). See [docs/multi-tenancy.md](docs/multi-tenancy.md).

## Kafka architecture
Nine spec topics plus two DLQs, keyed by `orderId` (locations by `driverId`), uniform event envelope with `correlationId`. `driver.unavailable` (driver goes offline/on break -> waiting orders are reassigned) and `notification.requested` (all notifications flow through it) are now produced and consumed. See [docs/kafka.md](docs/kafka.md).

## Redis architecture
GEO index per organization with freshness tracking (stale drivers are skipped, then evicted), per-driver assignment locks, fixed-window rate limits, a read-through cache with hit/miss metrics (dispatch settings, dashboard summary, geocoder), pub/sub for WebSocket fan-out. See [docs/redis.md](docs/redis.md).

## Dispatch algorithm
Deterministic weighted score (not machine learning): distance 0.40, availability 0.20, workload 0.15, vehicle match 0.15, rating 0.10 by default, configurable per organization. Straight-line distance only. See [docs/dispatch-engine.md](docs/dispatch-engine.md) and ADR-006.

## Concurrency handling
Redis lock in front, PostgreSQL driver row lock + workload count + order `@Version` behind it; Redis outage degrades speed, not correctness. `AssignmentConcurrencyIT` races 10 orders at one driver (expects exactly 1 success, or exactly 3 with workload 3, and also with the Redis lock bypassed). See ADR-007.

## Transactional outbox
State change and `outbox_events` row commit together; the relay polls with `SKIP LOCKED`, retries with exponential backoff, marks FAILED after 10 attempts. Delivery is at-least-once. See ADR-004.

## Idempotency
`processed_events (consumer, event_id)` ledger; effect and ledger commit together where possible. See ADR-008.

## Failure handling
3 retries with exponential backoff, then DLQ; DLQ messages stored and replayable via `/api/v1/admin/dead-letters`. Simulation controls (`DEV_TOOLS=true`) and the **expected** behaviour of each experiment are in [docs/failure-handling.md](docs/failure-handling.md). The experiments have not been run yet.

## Observability
Micrometer -> Prometheus -> Grafana dashboard (API, Kafka, Dispatch, Infrastructure). Correlation ids flow from HTTP into event envelopes. See [docs/observability.md](docs/observability.md).

## Load testing
Script: `load-tests/create-orders.js` (k6, constant arrival rate). Run at 100, 500 and 1000 orders/sec with `RATE_LIMIT_ORDERS=1000000` on the backend, and record real results here. **No results exist yet; this table is intentionally empty.**

| Rate | Requests/sec achieved | p50 | p95 | p99 | Error rate | Max Kafka lag | Dispatch latency p95 |
|---|---|---|---|---|---|---|---|
| 100/s | not measured | | | | | | |
| 500/s | not measured | | | | | | |
| 1000/s | not measured | | | | | | |

## Screenshots
Not captured yet. After running the demo, add screenshots of the landing page, dashboard, orders, dispatch, live map and analytics here.

## Local setup
```bash
cp .env.example .env            # set POSTGRES_PASSWORD, JWT_SECRET (>=32 bytes), GRAFANA_PASSWORD, DEMO_PASSWORD
# set SEED_DEMO=true in .env for demo data
docker compose up -d --build   # postgres, redis, kafka, kafka-ui, prometheus, grafana, redis-exporter, backend
cd frontend && npm install && npm run dev     # http://localhost:5173
```
URLs: app 5173, API 8080 (Swagger UI `/swagger-ui.html`), Kafka UI 8081, Prometheus 9090, Grafana 3000, Mailpit inbox 8025.
The backend container connects as the non-superuser role `fleetflow_app` so row-level security applies; the role is created by `infrastructure/postgres/01-app-role.sh` on a **fresh** database volume (`docker compose down -v` if you already have one). Running the backend outside Docker as the Postgres superuser works but RLS is then bypassed.
Demo login (after `SEED_DEMO=true`): `demo@fleetflow.dev` (owner), `dispatcher@fleetflow.dev`, `admin@fleetflow.dev`, `driver@fleetflow.dev` (opens the driver app), password = `DEMO_PASSWORD` (or a random one printed once in the backend log). Data is fictional. Seeded in-flight "awaiting dispatch" orders get dispatched by the sweeper after ~30 s.
Without Docker for the backend: `EVENTS_PUBLISHER=log` (default) logs events instead of using Kafka; dispatch consumers are then inactive.
Emails (verification, reset, invitations) go to Mailpit in compose (http://localhost:8025). Outside compose the default provider only logs them. See [docs/integrations.md](docs/integrations.md) for real SMTP, address search and map tiles.

## Environment variables
See [.env.example](.env.example). Backend also reads `DB_URL, DB_USER, DB_PASSWORD, DB_ADMIN_USER, DB_ADMIN_PASSWORD (Flyway), APP_DB_PASSWORD, MAIL_PROVIDER/HOST/PORT/USER/PASSWORD/FROM, GEOCODER_URL/USER_AGENT/COUNTRIES/MAX_PER_SECOND, JWT_SECRET, COOKIE_SECURE, CORS_ORIGINS, REDIS_HOST, REDIS_PORT, KAFKA_BOOTSTRAP, EVENTS_PUBLISHER (log|kafka), SEED_DEMO, DEMO_PASSWORD, DEV_TOOLS, RATE_LIMIT_ORDERS, APP_URL`. No secrets are committed.

## API documentation
OpenAPI/Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`. All endpoints are under `/api/v1`; WebSocket at `/ws/live?token=<access token>`. Errors use `{timestamp,status,error,message,path,correlationId,details}`.

## Verify it
1. `cd backend && mvn verify` (Docker required for Testcontainers). Start with `AssignmentConcurrencyIT`, `DispatchFlowIT`, `AccountFlowIT`.
2. `cd frontend && npm install && npm run build`.
3. `docker compose up -d --build`, sign up or use the demo login, create an order, watch it get assigned. Log in as `driver@fleetflow.dev` in a second browser to try the driver app.
4. Open Grafana and confirm each panel's metric name resolves in Prometheus.
5. Run the failure experiments and the k6 tests, then fill in the docs and the table above with what you measured.

## Known gaps / deviations from the original brief
- Unverified build (see top). No frontend tests. The new pieces (RLS, driver app, geocoder, SMTP, retention, cache, reassignment) have tests written but not run.
- Driver app is a web app: no push notifications, offline mode, native app or proof of delivery. GPS needs HTTPS outside localhost.
- The public Nominatim server and public OSM tiles are development-only; production needs your own instance/provider (see docs/integrations.md).
- Email has no bounce handling or retry queue; failures are logged and swallowed.
- RLS depends on the app connecting as a non-superuser role; system threads (Kafka consumers, schedulers, WebSocket handler) run in bypass mode.
- Access tokens of disabled users stay valid up to 15 minutes; WebSockets are authenticated at handshake only.
- Notifications need `EVENTS_PUBLISHER=kafka` (they are created by consumers).
- `processed_events` retention (30 days) must stay above Kafka retention plus any DLQ replay window.
- Distance and ETA are straight-line estimates. No IaC for AWS beyond the Dockerfile. Maps use Leaflet, not MapLibre; a custom component set is used instead of shadcn/ui.

## Future improvements
Retry topics; road-distance ETA and routing; native driver app with push and proof of delivery; email delivery queue with bounce handling; refresh-token device metadata (geo-IP); Terraform for AWS; frontend test suite.

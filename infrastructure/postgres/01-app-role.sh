#!/bin/sh
# Runs once on first database initialisation. Creates a NON-superuser role for the application so that row-level security applies
# (superusers bypass RLS). The migration owner (POSTGRES_USER) keeps DDL rights; the app role only gets data access.
set -e
psql -v ON_ERROR_STOP=1 -v pw="$APP_DB_PASSWORD" -v owner="$POSTGRES_USER" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'EOSQL'
CREATE ROLE fleetflow_app LOGIN PASSWORD :'pw' NOSUPERUSER NOBYPASSRLS;
GRANT USAGE ON SCHEMA public TO fleetflow_app;
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO fleetflow_app;
EOSQL

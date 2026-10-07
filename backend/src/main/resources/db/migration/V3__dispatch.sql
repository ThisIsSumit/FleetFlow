CREATE TABLE processed_events (
  consumer VARCHAR(64) NOT NULL,
  event_id UUID NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, event_id)
);
CREATE TABLE dispatch_settings (
  organization_id UUID PRIMARY KEY REFERENCES organizations(id),
  search_radius_km DOUBLE PRECISION NOT NULL DEFAULT 5 CHECK (search_radius_km > 0 AND search_radius_km <= 50),
  max_workload INT NOT NULL DEFAULT 1 CHECK (max_workload BETWEEN 1 AND 10),
  w_distance DOUBLE PRECISION NOT NULL DEFAULT 0.40,
  w_availability DOUBLE PRECISION NOT NULL DEFAULT 0.20,
  w_workload DOUBLE PRECISION NOT NULL DEFAULT 0.15,
  w_vehicle DOUBLE PRECISION NOT NULL DEFAULT 0.15,
  w_rating DOUBLE PRECISION NOT NULL DEFAULT 0.10,
  auto_dispatch BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE dead_letter_events (
  id UUID PRIMARY KEY,
  organization_id UUID,
  original_topic VARCHAR(80) NOT NULL,
  partition_key VARCHAR(64),
  payload TEXT NOT NULL,
  error VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  replayed_at TIMESTAMPTZ
);
CREATE INDEX ix_dlq_org ON dead_letter_events (organization_id, created_at DESC);
-- Workload is now configurable (>1 active order per driver), so the single-active-order index is replaced by
-- a row lock on the driver + a workload count checked inside the assignment transaction.
DROP INDEX ux_orders_active_driver;
CREATE INDEX ix_orders_driver_status ON orders (driver_id, status) WHERE driver_id IS NOT NULL;

CREATE TABLE vehicles (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  registration_number VARCHAR(32) NOT NULL,
  type VARCHAR(10) NOT NULL CHECK (type IN ('BIKE','CAR','VAN','TRUCK')),
  capacity_kg INT NOT NULL CHECK (capacity_kg > 0),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','MAINTENANCE','INACTIVE')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, registration_number)
);
CREATE TABLE drivers (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  user_id UUID REFERENCES users(id),
  vehicle_id UUID REFERENCES vehicles(id),
  name VARCHAR(120) NOT NULL,
  phone VARCHAR(20),
  status VARCHAR(12) NOT NULL DEFAULT 'OFFLINE' CHECK (status IN ('AVAILABLE','BUSY','OFFLINE','ON_BREAK')),
  rating NUMERIC(3,2) NOT NULL DEFAULT 4.50,
  last_lat DOUBLE PRECISION, last_lng DOUBLE PRECISION,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- a vehicle can be assigned to at most one driver
CREATE UNIQUE INDEX ux_drivers_vehicle ON drivers (vehicle_id) WHERE vehicle_id IS NOT NULL;
CREATE INDEX ix_drivers_org_status ON drivers (organization_id, status);
CREATE TABLE orders (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  customer_name VARCHAR(120) NOT NULL, customer_phone VARCHAR(20),
  pickup_address VARCHAR(300) NOT NULL, pickup_lat DOUBLE PRECISION NOT NULL, pickup_lng DOUBLE PRECISION NOT NULL,
  drop_address VARCHAR(300) NOT NULL, drop_lat DOUBLE PRECISION NOT NULL, drop_lng DOUBLE PRECISION NOT NULL,
  items TEXT NOT NULL,
  priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
  vehicle_requirement VARCHAR(10),
  scheduled_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL,
  driver_id UUID REFERENCES drivers(id),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_orders_org_created ON orders (organization_id, created_at DESC);
CREATE INDEX ix_orders_org_status ON orders (organization_id, status);
-- one active order per driver, enforced by the database (used by the dispatch slice)
CREATE UNIQUE INDEX ux_orders_active_driver ON orders (driver_id)
  WHERE driver_id IS NOT NULL AND status IN ('ASSIGNED','ARRIVED','IN_TRANSIT');
CREATE TABLE order_events (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  order_id UUID NOT NULL REFERENCES orders(id),
  from_status VARCHAR(20), to_status VARCHAR(20) NOT NULL,
  actor_user_id UUID, event_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_events_order ON order_events (order_id, created_at);
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL,
  aggregate_id UUID NOT NULL,
  event_type VARCHAR(48) NOT NULL,
  topic VARCHAR(64) NOT NULL,
  partition_key VARCHAR(64) NOT NULL,
  payload TEXT NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_error VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox_events (next_attempt_at) WHERE status = 'PENDING';

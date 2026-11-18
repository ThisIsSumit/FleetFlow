-- The dispatch sweeper scans DISPATCH_REQUESTED orders across all tenants by age every 15 s.
CREATE INDEX ix_orders_dispatch_waiting ON orders (updated_at) WHERE status = 'DISPATCH_REQUESTED';

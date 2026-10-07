ALTER TABLE auth_tokens ADD COLUMN driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN dispatch_attempts INT NOT NULL DEFAULT 0;
-- one DELIVERY_DELAYED notification per order, even if the async pipeline requests it twice
CREATE UNIQUE INDEX ux_notifications_delay ON notifications (order_id) WHERE type = 'DELIVERY_DELAYED';

CREATE TABLE auth_tokens (
  id UUID PRIMARY KEY,
  type VARCHAR(20) NOT NULL CHECK (type IN ('EMAIL_VERIFY','PASSWORD_RESET','INVITATION')),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  email VARCHAR(255), role VARCHAR(20),
  expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_auth_tokens_user ON auth_tokens (user_id, type);
CREATE INDEX ix_auth_tokens_org_type ON auth_tokens (organization_id, type) WHERE used_at IS NULL;
CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  type VARCHAR(32) NOT NULL, title VARCHAR(120) NOT NULL, message VARCHAR(300) NOT NULL,
  order_id UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_org ON notifications (organization_id, created_at DESC);
CREATE INDEX ix_notifications_order_type ON notifications (order_id, type);
CREATE TABLE notification_reads (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, notification_id)
);

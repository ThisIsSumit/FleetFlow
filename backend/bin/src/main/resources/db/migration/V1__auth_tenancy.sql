CREATE TABLE organizations (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE users (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name VARCHAR(120) NOT NULL,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','ADMIN','DISPATCHER','DRIVER')),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE INDEX ix_users_org ON users (organization_id);
CREATE TABLE refresh_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  previous_token_hash VARCHAR(64),
  user_agent VARCHAR(400),
  ip VARVARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_sessions_user ON refresh_sessions (user_id);
CREATE INDEX ix_sessions_prev ON refresh_sessions (previous_token_hash);

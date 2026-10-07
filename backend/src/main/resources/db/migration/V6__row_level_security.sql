-- Defence in depth: PostgreSQL itself refuses cross-tenant rows even if application code forgets a filter.
-- The app sets two session variables per connection (see TenantAwareDataSource):
--   app.current_org  = the authenticated user's organization (authenticated requests)
--   app.bypass_rls   = 'on' for system work with no user (Kafka consumers, schedulers, login/signup)
-- Unset variables mean NO rows are visible (safe default).
CREATE FUNCTION app_tenant_ok(org uuid) RETURNS boolean LANGUAGE sql STABLE AS $$
  SELECT current_setting('app.bypass_rls', true) = 'on' OR org = NULLIF(current_setting('app.current_org', true), '')::uuid
$$;
ALTER TABLE organizations ENABLE ROW LEVEL SECURITY; ALTER TABLE organizations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON organizations USING (app_tenant_ok(id)) WITH CHECK (app_tenant_ok(id));
DO $$ DECLARE t text; BEGIN
  FOREACH t IN ARRAY ARRAY['users','refresh_sessions','vehicles','drivers','orders','order_events','outbox_events','auth_tokens','notifications','dispatch_settings','dead_letter_events'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (app_tenant_ok(organization_id)) WITH CHECK (app_tenant_ok(organization_id))', t);
  END LOOP;
END $$;

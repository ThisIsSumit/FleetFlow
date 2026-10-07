package com.fleetflow.rls;
import com.fleetflow.security.AuthPrincipal;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import javax.sql.DataSource;
import java.sql.*;
/** On every connection checkout, tells PostgreSQL who is asking. Authenticated request thread -> scoped to that organization.
 *  No authenticated user (Kafka consumers, schedulers, login/signup, Flyway) -> system bypass. Costs one extra round trip per checkout. */
public class TenantAwareDataSource extends DelegatingDataSource {
  public TenantAwareDataSource(DataSource target) { super(target); }
  @Override public Connection getConnection() throws SQLException { return apply(super.getConnection()); }
  @Override public Connection getConnection(String u, String p) throws SQLException { return apply(super.getConnection(u, p)); }
  private Connection apply(Connection c) throws SQLException {
    String org = ""; String bypass = "on";
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a != null && a.getPrincipal() instanceof AuthPrincipal p) { org = p.organizationId().toString(); bypass = "off"; }
    try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.current_org', ?, false), set_config('app.bypass_rls', ?, false)")) {
      ps.setString(1, org); ps.setString(2, bypass); ps.execute();
    } catch (SQLException e) { try { c.close(); } catch (SQLException ignored) {} throw e; }
    return c;
  }
}

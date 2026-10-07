package com.fleetflow.rls;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
/** Proves the database itself enforces tenant isolation, independent of any application code. Uses a NON-superuser role because superusers bypass RLS. */
@Testcontainers
class RlsIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();
  static final String INSERT = "INSERT INTO orders(id, organization_id, customer_name, pickup_address, pickup_lat, pickup_lng, drop_address, drop_lat, drop_lng, items, priority, status) VALUES (?,?,'c','a',1,1,'b',1,1,'[]','NORMAL','CREATED')";
  @BeforeAll static void setup() throws Exception {
    Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()).load().migrate();
    try (Connection c = pg.createConnection(""); Statement s = c.createStatement()) {
      s.execute("CREATE ROLE rls_app LOGIN PASSWORD 'rls' NOSUPERUSER NOBYPASSRLS");
      s.execute("GRANT USAGE ON SCHEMA public TO rls_app"); s.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rls_app");
      for (UUID o : new UUID[]{A, B}) { s.execute("INSERT INTO organizations(id, name) VALUES ('" + o + "', 'org')");
        try (PreparedStatement ps = c.prepareStatement(INSERT)) { ps.setObject(1, UUID.randomUUID()); ps.setObject(2, o); ps.execute(); } }
    }
  }
  static Connection app(String org, String bypass) throws SQLException {
    Connection c = DriverManager.getConnection(pg.getJdbcUrl(), "rls_app", "rls");
    try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.current_org', ?, false), set_config('app.bypass_rls', ?, false)")) { ps.setString(1, org); ps.setString(2, bypass); ps.execute(); }
    return c;
  }
  static int count(Connection c, String where) throws SQLException { try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM orders" + where)) { r.next(); return r.getInt(1); } }
  @Test void noTenantContextMeansNoRows() throws Exception { try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), "rls_app", "rls")) { assertEquals(0, count(c, "")); } }
  @Test void tenantSeesOnlyItsOwnRows() throws Exception { try (Connection c = app(A.toString(), "off")) { assertEquals(1, count(c, "")); assertEquals(0, count(c, " WHERE organization_id = '" + B + "'")); } }
  @Test void crossTenantInsertIsRejectedByThePolicy() throws Exception {
    try (Connection c = app(A.toString(), "off"); PreparedStatement ps = c.prepareStatement(INSERT)) {
      ps.setObject(1, UUID.randomUUID()); ps.setObject(2, B);
      SQLException e = assertThrows(SQLException.class, ps::execute); assertTrue(e.getMessage().contains("row-level security"), e.getMessage());
    }
  }
  @Test void crossTenantUpdateTouchesNothing() throws Exception {
    try (Connection c = app(A.toString(), "off"); Statement s = c.createStatement()) { assertEquals(0, s.executeUpdate("UPDATE orders SET customer_name = 'hacked' WHERE organization_id = '" + B + "'")); }
  }
  @Test void systemBypassSeesEverything() throws Exception { try (Connection c = app("", "on")) { assertEquals(2, count(c, "")); } }
}

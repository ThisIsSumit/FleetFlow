package com.fleetflow.dashboard;
import com.fleetflow.security.TenantContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
/** Read-only aggregates. Every query is scoped by the organization taken from the JWT. */
@RestController @RequestMapping("/api/v1")
public class DashboardController {
  public record DayPoint(LocalDate date, long created, long delivered) {}
  public record Summary(long totalOrders, long activeDeliveries, long availableDrivers, long completedToday, long failedToday,
      Double avgDeliveryMinutes, Double avgAssignmentSeconds, Double completionRate,
      List<DayPoint> ordersByDay, Map<String, Long> driversByStatus, Map<String, Long> ordersByStatus) {}
  public record Component(String name, String status) {}
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.common.CacheService cache;
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.ops.KafkaHealth kafka;
  private final JdbcTemplate jdbc; private final StringRedisTemplate redis;
  public DashboardController(JdbcTemplate jdbc, StringRedisTemplate redis) { this.jdbc = jdbc; this.redis = redis; }

  private long count(String sql, Object... a) { Long v = jdbc.queryForObject(sql, Long.class, a); return v == null ? 0 : v; }
  private Map<String, Long> groups(String sql, UUID org) {
    var m = new LinkedHashMap<String, Long>();
    jdbc.query(sql, rs -> { m.put(rs.getString("k"), rs.getLong("c")); }, org);
    return m;
  }

  @GetMapping("/dashboard/summary") @PreAuthorize("hasAnyRole('OWNER','ADMIN','DISPATCHER')")
  public Summary summary() { UUID org = TenantContext.organizationId(); return cache.get("dashboard", org.toString(), Summary.class, java.time.Duration.ofSeconds(10), () -> compute(org)); }
  private Summary compute(UUID org) {
    var byStatus = groups("SELECT status AS k, count(*) AS c FROM orders WHERE organization_id = ? GROUP BY status", org);
    var byDriver = groups("SELECT status AS k, count(*) AS c FROM drivers WHERE organization_id = ? GROUP BY status", org);
    long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
    long active = byStatus.getOrDefault("ASSIGNED", 0L) + byStatus.getOrDefault("ARRIVED", 0L) + byStatus.getOrDefault("IN_TRANSIT", 0L);
    long delivered = byStatus.getOrDefault("DELIVERED", 0L), closed = delivered + byStatus.getOrDefault("FAILED", 0L) + byStatus.getOrDefault("CANCELLED", 0L);
    long doneToday = count("SELECT count(*) FROM orders WHERE organization_id = ? AND status = 'DELIVERED' AND updated_at >= date_trunc('day', now())", org);
    long failedToday = count("SELECT count(*) FROM orders WHERE organization_id = ? AND status = 'FAILED' AND updated_at >= date_trunc('day', now())", org);
    Double avgDelivery = jdbc.queryForObject("""
        SELECT avg(extract(epoch FROM (d.created_at - c.created_at)) / 60) FROM order_events d
        JOIN order_events c ON c.order_id = d.order_id AND c.to_status = 'CREATED'
        WHERE d.organization_id = ? AND d.to_status = 'DELIVERED'""", Double.class, org);
    Double avgAssign = jdbc.queryForObject("""
        SELECT avg(extract(epoch FROM (a.created_at - c.created_at))) FROM order_events a
        JOIN order_events c ON c.order_id = a.order_id AND c.to_status = 'CREATED'
        WHERE a.organization_id = ? AND a.to_status = 'ASSIGNED'""", Double.class, org);
    var created = new HashMap<LocalDate, Long>(); var deliveredBy = new HashMap<LocalDate, Long>();
    jdbc.query("SELECT created_at::date AS d, count(*) AS c FROM orders WHERE organization_id = ? AND created_at >= now() - interval '7 days' GROUP BY 1",
        rs -> { created.put(rs.getDate("d").toLocalDate(), rs.getLong("c")); }, org);
    jdbc.query("SELECT updated_at::date AS d, count(*) AS c FROM orders WHERE organization_id = ? AND status = 'DELIVERED' AND updated_at >= now() - interval '7 days' GROUP BY 1",
        rs -> { deliveredBy.put(rs.getDate("d").toLocalDate(), rs.getLong("c")); }, org);
    var days = new ArrayList<DayPoint>(); LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (int i = 6; i >= 0; i--) { var d = today.minusDays(i); days.add(new DayPoint(d, created.getOrDefault(d, 0L), deliveredBy.getOrDefault(d, 0L))); }
    return new Summary(total, active, byDriver.getOrDefault("AVAILABLE", 0L), doneToday, failedToday, avgDelivery, avgAssign,
        closed == 0 ? null : (double) delivered / closed, days, byDriver, byStatus);
  }

  /** Coarse, tenant-agnostic component status. Deliberately returns no counts (they would span tenants). */
  @GetMapping("/system/health")
  public List<Component> health() {
    String db, redisStatus, pipeline;
    try { jdbc.queryForObject("SELECT 1", Integer.class); db = "UP"; } catch (Exception e) { db = "DOWN"; }
    try (RedisConnection c = redis.getRequiredConnectionFactory().getConnection()) { c.ping(); redisStatus = "UP"; } catch (Exception e) { redisStatus = "DOWN"; }
    try {
      Boolean bad = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM outbox_events WHERE status = 'FAILED' OR (status = 'PENDING' AND created_at < now() - interval '60 seconds'))", Boolean.class);
      pipeline = Boolean.TRUE.equals(bad) ? "DEGRADED" : "UP";
    } catch (Exception e) { pipeline = "DOWN"; }
    return List.of(new Component("Database", db), new Component("Redis", redisStatus), new Component("Kafka", kafka.broker()), new Component("Event pipeline", pipeline), new Component("Dispatch", kafka.consumers()));
  }
}

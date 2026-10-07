package com.fleetflow.analytics;
import com.fleetflow.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class AnalyticsController {
  public record Day(LocalDate date, long created, long delivered, long cancelled, long failed) {}
  public record DriverLoad(UUID driverId, String name, long orders, long delivered) {}
  public record Analytics(int days, List<Day> series, Double completionRate, Double cancellationRate, Double avgDeliveryMinutes, Double avgAssignmentSeconds,
      Double driverUtilization, Double avgOrdersPerDriver, List<DriverLoad> ordersPerDriver) {}
  private final JdbcTemplate jdbc; public AnalyticsController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  private Map<LocalDate, Long> perDay(String sql, Object... args) { var m = new HashMap<LocalDate, Long>(); jdbc.query(sql, rs -> { m.merge(rs.getDate("d").toLocalDate(), rs.getLong("c"), Long::sum); }, args); return m; }
  @GetMapping("/analytics") @PreAuthorize("hasAnyRole('OWNER','ADMIN','DISPATCHER')")
  public Analytics get(@RequestParam(defaultValue = "14") int days) {
    days = Math.max(1, Math.min(days, 90)); UUID org = TenantContext.organizationId();
    var created = perDay("SELECT created_at::date AS d, count(*) AS c FROM orders WHERE organization_id = ? AND created_at >= now() - make_interval(days => ?) GROUP BY 1", org, days);
    String closed = "SELECT updated_at::date AS d, count(*) AS c FROM orders WHERE organization_id = ? AND status = ? AND updated_at >= now() - make_interval(days => ?) GROUP BY 1";
    var delivered = perDay(closed, org, "DELIVERED", days); var cancelled = perDay(closed, org, "CANCELLED", days); var failed = perDay(closed, org, "FAILED", days);
    var series = new ArrayList<Day>(); LocalDate today = LocalDate.now(ZoneOffset.UTC); long cr = 0, de = 0, ca = 0, fa = 0;
    for (int i = days - 1; i >= 0; i--) { var d = today.minusDays(i);
      var p = new Day(d, created.getOrDefault(d, 0L), delivered.getOrDefault(d, 0L), cancelled.getOrDefault(d, 0L), failed.getOrDefault(d, 0L)); series.add(p); cr += p.created(); de += p.delivered(); ca += p.cancelled(); fa += p.failed(); }
    Double avgDel = jdbc.queryForObject("SELECT avg(extract(epoch FROM (d.created_at - c.created_at)) / 60) FROM order_events d JOIN order_events c ON c.order_id = d.order_id AND c.to_status = 'CREATED' WHERE d.organization_id = ? AND d.to_status = 'DELIVERED' AND d.created_at >= now() - make_interval(days => ?)", Double.class, org, days);
    Double avgAsg = jdbc.queryForObject("SELECT avg(extract(epoch FROM (a.created_at - c.created_at))) FROM order_events a JOIN order_events c ON c.order_id = a.order_id AND c.to_status = 'CREATED' WHERE a.organization_id = ? AND a.to_status = 'ASSIGNED' AND a.created_at >= now() - make_interval(days => ?)", Double.class, org, days);
    Long busy = jdbc.queryForObject("SELECT count(*) FROM drivers WHERE organization_id = ? AND status = 'BUSY'", Long.class, org);
    Long online = jdbc.queryForObject("SELECT count(*) FROM drivers WHERE organization_id = ? AND status <> 'OFFLINE'", Long.class, org);
    var load = jdbc.query("SELECT d.id, d.name, count(o.id) AS orders, count(o.id) FILTER (WHERE o.status = 'DELIVERED') AS delivered FROM drivers d LEFT JOIN orders o ON o.driver_id = d.id AND o.created_at >= now() - make_interval(days => ?) WHERE d.organization_id = ? GROUP BY d.id, d.name ORDER BY orders DESC, d.name LIMIT 20",
        (rs, i) -> new DriverLoad(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("orders"), rs.getLong("delivered")), days, org);
    long closedCount = de + ca + fa; long assigned = load.stream().mapToLong(DriverLoad::orders).sum();
    return new Analytics(days, series, closedCount == 0 ? null : (double) de / closedCount, cr == 0 ? null : (double) ca / cr, avgDel, avgAsg,
        online == null || online == 0 ? null : (double) busy / online, load.isEmpty() ? null : (double) assigned / load.size(), load);
  }
}

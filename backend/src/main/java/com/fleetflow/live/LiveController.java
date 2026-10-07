package com.fleetflow.live;
import com.fleetflow.dispatch.DriverGeoService;
import com.fleetflow.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/live")
public class LiveController {
  public record LiveDriver(UUID id, String name, String status, UUID vehicleId, String vehicleType, String registration, Double lat, Double lng, UUID currentOrderId) {}
  public record LiveOrder(UUID id, String customerName, String status, String pickupAddress, double pickupLat, double pickupLng, String dropAddress, double dropLat, double dropLng, UUID driverId) {}
  public record Snapshot(List<LiveDriver> drivers, List<LiveOrder> orders) {}
  private final JdbcTemplate jdbc; private final DriverGeoService geo;
  public LiveController(JdbcTemplate jdbc, DriverGeoService geo) { this.jdbc = jdbc; this.geo = geo; }
  @GetMapping("/snapshot") @PreAuthorize("hasAnyRole('OWNER','ADMIN','DISPATCHER')")
  public Snapshot snapshot() {
    UUID org = TenantContext.organizationId();
    var orders = jdbc.query("SELECT id, customer_name, status, pickup_address, pickup_lat, pickup_lng, drop_address, drop_lat, drop_lng, driver_id FROM orders WHERE organization_id = ? AND status IN ('DISPATCH_REQUESTED','ASSIGNED','ARRIVED','IN_TRANSIT') ORDER BY created_at DESC LIMIT 200",
        (rs, i) -> new LiveOrder(rs.getObject("id", UUID.class), rs.getString("customer_name"), rs.getString("status"), rs.getString("pickup_address"), rs.getDouble("pickup_lat"), rs.getDouble("pickup_lng"),
            rs.getString("drop_address"), rs.getDouble("drop_lat"), rs.getDouble("drop_lng"), rs.getObject("driver_id", UUID.class)), org);
    var current = new HashMap<UUID, UUID>();
    for (var o : orders) if (o.driverId() != null) current.putIfAbsent(o.driverId(), o.id());
    var rows = jdbc.queryForList("SELECT d.id, d.name, d.status, d.vehicle_id, v.type AS vtype, v.registration_number AS reg FROM drivers d LEFT JOIN vehicles v ON v.id = d.vehicle_id WHERE d.organization_id = ? ORDER BY d.name LIMIT 500", org);
    var pos = geo.positions(org, rows.stream().map(r -> (UUID) r.get("id")).toList());
    var drivers = rows.stream().map(r -> { UUID id = (UUID) r.get("id"); var p = pos.get(id);
      return new LiveDriver(id, (String) r.get("name"), (String) r.get("status"), (UUID) r.get("vehicle_id"), (String) r.get("vtype"), (String) r.get("reg"), p == null ? null : p.getY(), p == null ? null : p.getX(), current.get(id)); }).toList();
    return new Snapshot(drivers, orders);
  }
}

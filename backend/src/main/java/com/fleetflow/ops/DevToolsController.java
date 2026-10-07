package com.fleetflow.ops;
import com.fleetflow.common.ApiException;
import com.fleetflow.dispatch.DriverGeoService;
import com.fleetflow.outbox.EventPublisher;
import com.fleetflow.security.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** Failure-simulation controls. Exists only when fleetflow.dev-tools.enabled=true and is OWNER-only. Never enable in production. */
@RestController @RequestMapping("/api/v1/dev") @PreAuthorize("hasRole('OWNER')")
@ConditionalOnProperty(name = "fleetflow.dev-tools.enabled", havingValue = "true")
public class DevToolsController {
  public record FaultBody(FaultInjector.Fault fault, boolean enabled, Long param) {}
  private final FaultInjector faults; private final JdbcTemplate jdbc; private final EventPublisher publisher; private final DriverGeoService geo;
  private final com.fleetflow.fleet.FleetService fleet;
  public DevToolsController(FaultInjector f, JdbcTemplate j, EventPublisher p, DriverGeoService g, com.fleetflow.fleet.FleetService fleet) { faults = f; jdbc = j; publisher = p; geo = g; this.fleet = fleet; }
  @GetMapping("/faults") public Map<FaultInjector.Fault, Long> active() { return faults.active(); }
  @PostMapping("/faults") public Map<FaultInjector.Fault, Long> set(@RequestBody FaultBody b) {
    if (b.fault() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "fault is required");
    if (b.enabled()) faults.enable(b.fault(), b.param() == null ? 1 : b.param()); else faults.disable(b.fault());
    return faults.active();
  }
  /** Re-publishes this organization's most recent sent event to prove consumers ignore duplicates. */
  @PostMapping("/actions/duplicate-last-event") public Map<String, Object> duplicate() throws Exception {
    var r = jdbc.queryForList("SELECT topic, partition_key, payload FROM outbox_events WHERE organization_id = ? AND status = 'SENT' ORDER BY sent_at DESC LIMIT 1", TenantContext.organizationId());
    if (r.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No sent events yet");
    publisher.publish((String) r.get(0).get("topic"), (String) r.get(0).get("partition_key"), (String) r.get(0).get("payload"));
    return Map.of("republishedTopic", r.get(0).get("topic"));
  }
  @PostMapping("/actions/disconnect-driver/{id}") public void disconnect(@PathVariable UUID id) { fleet.setDriverStatus(id, com.fleetflow.fleet.Enums.DriverStatus.OFFLINE); } // emits driver.unavailable
}

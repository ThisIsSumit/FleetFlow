package com.fleetflow.fleet;
import com.fleetflow.fleet.Enums.*;
import com.fleetflow.fleet.FleetDtos.*;
import com.fleetflow.live.LocationService;
import com.fleetflow.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
/** Everything a driver can do, and nothing else. The driver is always derived from the authenticated user, never from the request. */
@RestController @RequestMapping("/api/v1/driver") @PreAuthorize("hasRole('DRIVER')")
public class DriverPortalController {
  public record StatusBody(@NotNull DriverStatus status) {}
  public record OrderStatusBody(@NotNull OrderStatus status) {}
  public record LocationBody(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude, @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
  private final FleetService fleet; private final LocationService locations;
  public DriverPortalController(FleetService fleet, LocationService locations) { this.fleet = fleet; this.locations = locations; }
  @GetMapping("/me") public FleetService.DriverHome me() { return fleet.driverHome(); }
  @PatchMapping("/status") public DriverDto status(@Valid @RequestBody StatusBody b) { return fleet.setOwnStatus(b.status()); }
  @PostMapping("/orders/{id}/status") public OrderDto orderStatus(@PathVariable UUID id, @Valid @RequestBody OrderStatusBody b) { return fleet.changeOwnOrderStatus(id, b.status()); }
  @PostMapping("/location") public ResponseEntity<Void> location(@Valid @RequestBody LocationBody b) { locations.update(TenantContext.organizationId(), fleet.ownDriverId(), b.latitude(), b.longitude()); return ResponseEntity.noContent().build(); }
}

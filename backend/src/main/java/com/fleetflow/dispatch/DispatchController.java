package com.fleetflow.dispatch;
import com.fleetflow.common.ApiException;
import com.fleetflow.dispatch.DispatchScorer.Weights;
import com.fleetflow.dispatch.DispatchSettings.Values;
import com.fleetflow.fleet.FleetRepos.DriverRepository;
import com.fleetflow.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1")
public class DispatchController {
  public record SettingsDto(@DecimalMin("0.1") @DecimalMax("50") double searchRadiusKm, @Min(1) @Max(10) int maxDriverWorkload,
      @NotNull @Valid WeightsDto weights, boolean autoDispatchEnabled) {}
  public record WeightsDto(@PositiveOrZero double distance, @PositiveOrZero double availability, @PositiveOrZero double workload, @PositiveOrZero double vehicleMatch, @PositiveOrZero double rating) {}
  public record LocationRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude, @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
  private final SettingsService settings; private final com.fleetflow.live.LocationService locations;
  public DispatchController(SettingsService s, com.fleetflow.live.LocationService l) { settings = s; locations = l; }

  private static SettingsDto dto(Values v) { var w = v.weights(); return new SettingsDto(v.radiusKm(), v.maxWorkload(), new WeightsDto(w.distance(), w.availability(), w.workload(), w.vehicle(), w.rating()), v.autoDispatch()); }
  @GetMapping("/settings/dispatch") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public SettingsDto get() { return dto(settings.get(TenantContext.organizationId())); }
  @PutMapping("/settings/dispatch") @PreAuthorize("hasRole('OWNER')")
  public SettingsDto put(@Valid @RequestBody SettingsDto b) {
    var w = b.weights();
    return dto(settings.save(TenantContext.organizationId(), new Values(b.searchRadiusKm(), b.maxDriverWorkload(), new Weights(w.distance(), w.availability(), w.workload(), w.vehicleMatch(), w.rating()), b.autoDispatchEnabled())));
  }
  /** REST stand-in for the WebSocket location channel (slice 4). */
  @PostMapping("/drivers/{id}/location") @PreAuthorize("hasAnyRole('OWNER','ADMIN','DISPATCHER')")
  public ResponseEntity<Void> location(@PathVariable UUID id, @Valid @RequestBody LocationRequest b) {
    locations.update(TenantContext.organizationId(), id, b.latitude(), b.longitude());
    return ResponseEntity.noContent().build();
  }
}

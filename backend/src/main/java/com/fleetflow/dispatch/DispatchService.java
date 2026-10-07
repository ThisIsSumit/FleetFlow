package com.fleetflow.dispatch;
import com.fleetflow.dispatch.DispatchScorer.Candidate;
import com.fleetflow.fleet.Entities.*;
import com.fleetflow.fleet.Enums.*;
import com.fleetflow.fleet.FleetRepos.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class DispatchService {
  public enum Result { SKIPPED, NO_CANDIDATE, CONTENDED, ASSIGNED }
  private final OrderRepository orders; private final DriverRepository drivers; private final VehicleRepository vehicles;
  private final DriverGeoService geo; private final SettingsService settings; private final AssignmentService assignment;
  public DispatchService(OrderRepository o, DriverRepository d, VehicleRepository v, DriverGeoService g, SettingsService s, AssignmentService a) { orders = o; drivers = d; vehicles = v; geo = g; settings = s; assignment = a; }

  /** find nearby -> filter -> score -> attempt atomic assignment in rank order. Deliberately not @Transactional (see AssignmentService). */
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.ops.FleetMetrics metrics;
  public Result dispatch(UUID org, UUID orderId) {
    metrics.dispatchAttempts.increment();
    Result r = metrics.dispatchLatency.record(() -> dispatchInternal(org, orderId));
    if (r == Result.ASSIGNED) metrics.dispatchSuccess.increment();
    return r;
  }
  Result dispatchInternal(UUID org, UUID orderId) {
    var order = orders.findByIdAndOrganizationId(orderId, org).orElse(null);
    if (order == null || order.status != OrderStatus.DISPATCH_REQUESTED) return Result.SKIPPED;
    var cfg = settings.get(org);
    if (!cfg.autoDispatch()) return Result.SKIPPED;
    var near = geo.nearby(org, order.pickupLat, order.pickupLng, cfg.radiusKm());
    if (near.isEmpty()) return Result.NO_CANDIDATE;
    var dist = near.stream().collect(Collectors.toMap(DriverGeoService.Nearby::driverId, DriverGeoService.Nearby::distanceKm));
    var found = drivers.findAllById(dist.keySet()).stream().filter(d -> d.organizationId.equals(org) && d.status == DriverStatus.AVAILABLE).toList();
    var vmap = vehicles.findAllById(found.stream().map(d -> d.vehicleId).filter(Objects::nonNull).toList()).stream()
        .filter(v -> v.organizationId.equals(org) && v.status == VehicleStatus.ACTIVE).collect(Collectors.toMap(v -> v.id, v -> v.type));
    var cands = found.stream().map(d -> new Candidate(d.id, dist.get(d.id), (int) orders.countByDriverIdAndStatusIn(d.id, DispatchTx.ACTIVE), true,
        d.vehicleId == null ? null : vmap.get(d.vehicleId), d.rating.doubleValue())).toList();
    var ranked = DispatchScorer.rank(cands, cfg.weights(), cfg.radiusKm(), cfg.maxWorkload(), order.vehicleRequirement);
    if (ranked.isEmpty()) return Result.NO_CANDIDATE;
    for (var s : ranked) if (assignment.tryAssign(org, orderId, s.driverId())) return Result.ASSIGNED;
    return Result.CONTENDED;
  }
}

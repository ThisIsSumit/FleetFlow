package com.fleetflow.dispatch;
import com.fleetflow.common.ApiException;
import com.fleetflow.fleet.FleetRepos.OrderRepository;
import com.fleetflow.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/dispatch/orders") @PreAuthorize("hasAnyRole('OWNER','ADMIN','DISPATCHER')")
public class DispatchOpsController {
  public record AssignBody(@NotNull UUID driverId) {}
  private final OrderRepository orders; private final AssignmentService assignment; private final DispatchTx tx;
  public DispatchOpsController(OrderRepository o, AssignmentService a, DispatchTx t) { orders = o; assignment = a; tx = t; }
  /** Manual override: same atomic assignment path as auto-dispatch (locks, workload check), but the dispatcher picks the driver. */
  @PostMapping("/{id}/assign")
  public ResponseEntity<Void> assign(@PathVariable UUID id, @Valid @RequestBody AssignBody b) {
    UUID org = TenantContext.organizationId();
    orders.findByIdAndOrganizationId(id, org).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Order not found"));
    if (!assignment.tryAssign(org, id, b.driverId())) throw new ApiException(HttpStatus.CONFLICT, "ASSIGNMENT_FAILED", "Driver is unavailable, at capacity, or the order is not awaiting dispatch");
    return ResponseEntity.noContent().build();
  }
  @PostMapping("/{id}/redispatch")
  public ResponseEntity<Void> redispatch(@PathVariable UUID id) {
    if (!tx.redispatch(TenantContext.organizationId(), id)) throw new ApiException(HttpStatus.CONFLICT, "NOT_AWAITING_DISPATCH", "Order is not awaiting dispatch");
    return ResponseEntity.accepted().build();
  }
}

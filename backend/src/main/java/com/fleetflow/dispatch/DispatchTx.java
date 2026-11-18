package com.fleetflow.dispatch;
import com.fleetflow.fleet.Entities.*;
import com.fleetflow.fleet.Enums.*;
import com.fleetflow.fleet.FleetRepos.*;
import com.fleetflow.notify.NotificationRequester;
import com.fleetflow.outbox.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
@Service
public class DispatchTx {
  static final Set<OrderStatus> ACTIVE = EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.ARRIVED, OrderStatus.IN_TRANSIT);
  private final DriverRepository drivers; private final OrderRepository orders; private final OrderEventRepository events;
  private final OutboxService outbox; private final SettingsService settings; private final EntityManager em;
  @Autowired private com.fleetflow.ops.FaultInjector faults;
  @Autowired private com.fleetflow.ops.FleetMetrics metrics;
  @Autowired private NotificationRequester requester;
  public DispatchTx(DriverRepository d, OrderRepository o, OrderEventRepository e, OutboxService ob, SettingsService s, EntityManager em) {
    drivers = d; orders = o; events = e; outbox = ob; settings = s; this.em = em;
  }

  private void timeline(DeliveryOrder o, OrderStatus from, OrderStatus to, UUID eventId) {
    var e = new OrderEvent(); e.organizationId = o.organizationId; e.orderId = o.id; e.fromStatus = from; e.toStatus = to; e.eventId = eventId; events.save(e);
  }
  /** CREATED -> DISPATCH_REQUESTED. Joins the caller's transaction (idempotency ledger + state change commit together). */
  @Transactional
  public void requestDispatch(UUID org, UUID orderId) {
    var o = orders.findByIdAndOrganizationId(orderId, org).orElse(null);
    if (o == null || o.status != OrderStatus.CREATED) return;
    o.status = OrderStatus.DISPATCH_REQUESTED; o.updatedAt = Instant.now();
    UUID ev = outbox.enqueue(org, orderId, "DISPATCH_REQUESTED", Topics.DISPATCH_REQUESTED, orderId.toString(), Map.of("orderId", orderId));
    timeline(o, OrderStatus.CREATED, OrderStatus.DISPATCH_REQUESTED, ev);
  }
  /**
   * The atomic assignment. REQUIRES_NEW so the commit happens BEFORE the Redis lock is released by the caller.
   * Correctness comes from (1) the row lock on the driver, (2) workload counted under that lock, (3) @Version on the order.
   * The row lock is taken via EntityManager.find(..., PESSIMISTIC_WRITE) rather than a @Lock/@Query repository method:
   * Hibernate 6.5 silently compiles this simple a "by id" JPQL shape down to a native query, and JPA forbids setting
   * a lock mode on a native query, so the Spring Data @Lock annotation throws IllegalStateException at runtime.
   * EntityManager.find is the spec-supported path and is unaffected by that optimization.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean assign(UUID org, UUID orderId, UUID driverId) {
    faults.maybeFail(com.fleetflow.ops.FaultInjector.Fault.DB_TIMEOUT);
    var d = em.find(Driver.class, driverId, LockModeType.PESSIMISTIC_WRITE);
    if (d != null && !d.organizationId.equals(org)) d = null; // cross-tenant id behaves like "not found"
    if (d == null || d.status != DriverStatus.AVAILABLE) return false;
    var o = orders.findByIdAndOrganizationId(orderId, org).orElse(null);
    if (o == null || o.status != OrderStatus.DISPATCH_REQUESTED) return false;
    int max = settings.get(org).maxWorkload();
    long active = orders.countByDriverIdAndStatusIn(driverId, ACTIVE);
    if (active >= max) return false;
    o.status = OrderStatus.ASSIGNED; o.driverId = driverId; o.updatedAt = Instant.now();
    if (active + 1 >= max) d.status = DriverStatus.BUSY;
    UUID ev = outbox.enqueue(org, orderId, "DRIVER_ASSIGNED", Topics.DRIVER_ASSIGNED, orderId.toString(), Map.of("orderId", orderId, "driverId", driverId));
    timeline(o, OrderStatus.DISPATCH_REQUESTED, OrderStatus.ASSIGNED, ev);
    return true;
  }

  /** Orders that found no driver. Cross-tenant on purpose: this is a system job. */
  @Transactional(readOnly = true)
  public List<UUID> stuckOrderIds(Instant olderThan) { return orders.findSweepableIds(olderThan); }

  /** One order per transaction, so one optimistic-lock conflict cannot roll back the rest of the sweep.
   *  Re-emits dispatch.requested, or, once maxAttempts is exceeded, fails the order and alerts a human. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void resweepOne(UUID orderId, int maxAttempts) {
    var o = orders.findById(orderId).orElse(null);
    if (o == null || o.status != OrderStatus.DISPATCH_REQUESTED) return;
    // Manual-dispatch organizations: the order waits for a human, so it must never burn retries or be auto-failed.
    if (!settings.get(o.organizationId).autoDispatch()) return;
    o.dispatchAttempts++; o.updatedAt = Instant.now();
    if (o.dispatchAttempts > maxAttempts) {
      o.status = OrderStatus.FAILED;
      UUID ev = outbox.enqueue(o.organizationId, o.id, "DELIVERY_FAILED", Topics.DELIVERY_CANCELLED, o.id.toString(), Map.of("orderId", o.id, "reason", "NO_DRIVER_AVAILABLE", "from", OrderStatus.DISPATCH_REQUESTED, "to", OrderStatus.FAILED));
      timeline(o, OrderStatus.DISPATCH_REQUESTED, OrderStatus.FAILED, ev);
      metrics.ordersFailed.increment();
      requester.request(o.organizationId, "SYSTEM_ALERT", "No driver found", "Order for " + o.customerName + " failed: no driver became available after " + maxAttempts + " attempts.", o.id);
      return;
    }
    outbox.enqueue(o.organizationId, o.id, "DISPATCH_REQUESTED", Topics.DISPATCH_REQUESTED, o.id.toString(), Map.of("orderId", o.id, "retry", true, "attempt", o.dispatchAttempts));
  }
  /** Operator action: emit a fresh dispatch.requested for an order still waiting. Resets the automatic-retry budget. */
  @Transactional
  public boolean redispatch(UUID org, UUID orderId) {
    var o = orders.findByIdAndOrganizationId(orderId, org).orElse(null);
    if (o == null || o.status != OrderStatus.DISPATCH_REQUESTED) return false;
    o.updatedAt = Instant.now(); o.dispatchAttempts = 0;
    outbox.enqueue(org, orderId, "DISPATCH_REQUESTED", Topics.DISPATCH_REQUESTED, orderId.toString(), Map.of("orderId", orderId, "retry", true));
    return true;
  }
  /** Driver went offline/on break: not-yet-picked-up orders return to the queue; picked-up ones alert an operator. Returns {requeued, inProgress}. */
  @Transactional
  public int[] reassignFromDriver(UUID org, UUID driverId) {
    var waiting = orders.findByDriverIdAndOrganizationIdAndStatusIn(driverId, org, EnumSet.of(OrderStatus.ASSIGNED));
    for (var o : waiting) {
      o.status = OrderStatus.DISPATCH_REQUESTED; o.driverId = null; o.updatedAt = Instant.now(); o.dispatchAttempts = 0;
      UUID ev = outbox.enqueue(org, o.id, "DISPATCH_REQUESTED", Topics.DISPATCH_REQUESTED, o.id.toString(), Map.of("orderId", o.id, "reason", "DRIVER_UNAVAILABLE"));
      timeline(o, OrderStatus.ASSIGNED, OrderStatus.DISPATCH_REQUESTED, ev);
      requester.request(org, "SYSTEM_ALERT", "Order reassigned", "The driver became unavailable, so the order for " + o.customerName + " is being reassigned.", o.id);
    }
    var inFlight = orders.findByDriverIdAndOrganizationIdAndStatusIn(driverId, org, EnumSet.of(OrderStatus.ARRIVED, OrderStatus.IN_TRANSIT));
    for (var o : inFlight) requester.request(org, "SYSTEM_ALERT", "Driver unavailable mid-delivery", "The driver for " + o.customerName + " went offline while the delivery was in progress. Please follow up.", o.id);
    return new int[]{waiting.size(), inFlight.size()};
  }
}
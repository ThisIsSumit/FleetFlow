package com.fleetflow.fleet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.common.ApiException;
import com.fleetflow.fleet.Entities.*;
import com.fleetflow.fleet.Enums.*;
import com.fleetflow.fleet.FleetDtos.*;
import com.fleetflow.fleet.FleetRepos.*;
import com.fleetflow.outbox.*;
import com.fleetflow.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
/** Every method derives the tenant from TenantContext; no organizationId ever comes from a request. */
@Service
public class FleetService {
  private static final Set<OrderStatus> MANUAL = EnumSet.of(OrderStatus.ARRIVED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.FAILED);
  private final VehicleRepository vehicles; private final DriverRepository drivers; private final OrderRepository orders;
  private final OrderEventRepository orderEvents; private final OutboxService outbox; private final ObjectMapper om;
  private final com.fleetflow.dispatch.DriverGeoService geo;
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.ops.FleetMetrics metrics;

  public FleetService(VehicleRepository v, DriverRepository d, OrderRepository o, OrderEventRepository oe, OutboxService ob, ObjectMapper om, com.fleetflow.dispatch.DriverGeoService geo) {
    this.vehicles = v; this.drivers = d; this.orders = o; this.orderEvents = oe; this.outbox = ob; this.om = om; this.geo = geo;
  }
  private static ApiException notFound(String what) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found"); }
  private static Pageable page(int page, int size, String sort) {
    Set<String> ok = Set.of("createdAt", "priority", "status", "customerName");
    return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, ok.contains(sort) ? sort : "createdAt"));
  }

  // ---- vehicles ----
  @Transactional
  public VehicleDto createVehicle(VehicleRequest r) {
    UUID org = TenantContext.organizationId();
    if (vehicles.existsByOrganizationIdAndRegistrationNumberIgnoreCase(org, r.registrationNumber()))
      throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_REGISTRATION", "Registration number already exists");
    var v = new Vehicle(); v.organizationId = org; v.registrationNumber = r.registrationNumber().trim().toUpperCase();
    v.type = r.type(); v.capacityKg = r.capacityKg(); if (r.status() != null) v.status = r.status();
    return vehicleDto(vehicles.save(v), null);
  }
  @Transactional
  public VehicleDto updateVehicle(UUID id, VehicleRequest r) {
    var v = vehicles.findByIdAndOrganizationId(id, TenantContext.organizationId()).orElseThrow(() -> notFound("Vehicle"));
    v.type = r.type(); v.capacityKg = r.capacityKg(); if (r.status() != null) v.status = r.status();
    return vehicleDto(v, null);
  }
  @Transactional(readOnly = true)
  public PageDto<VehicleDto> listVehicles(int page, int size) {
    UUID org = TenantContext.organizationId();
    var p = vehicles.findByOrganizationId(org, page(page, size, "createdAt"));
    var byVehicle = drivers.findByOrganizationIdAndVehicleIdIn(org, p.map(v -> v.id).getContent()).stream().collect(Collectors.toMap(d -> d.vehicleId, d -> d.id));
    return PageDto.of(p.map(v -> vehicleDto(v, byVehicle.get(v.id))));
  }
  private VehicleDto vehicleDto(Vehicle v, UUID driverId) { return new VehicleDto(v.id, v.registrationNumber, v.type, v.capacityKg, v.status, driverId); }

  // ---- drivers ----
  private void checkVehicle(UUID vehicleId, UUID org) {
    if (vehicleId != null && vehicles.findByIdAndOrganizationId(vehicleId, org).isEmpty()) throw notFound("Vehicle"); // cross-tenant ids look like 404
  }
  @Transactional
  public DriverDto createDriver(DriverRequest r) {
    UUID org = TenantContext.organizationId(); checkVehicle(r.vehicleId(), org);
    var d = new Driver(); d.organizationId = org; d.name = r.name().trim(); d.phone = r.phone(); d.vehicleId = r.vehicleId();
    try { return driverDto(drivers.saveAndFlush(d)); }
    catch (DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_TAKEN", "Vehicle is already assigned to a driver"); }
  }
  @Transactional
  public DriverDto updateDriver(UUID id, DriverRequest r) {
    UUID org = TenantContext.organizationId(); checkVehicle(r.vehicleId(), org);
    var d = drivers.findByIdAndOrganizationId(id, org).orElseThrow(() -> notFound("Driver"));
    d.name = r.name().trim(); d.phone = r.phone(); d.vehicleId = r.vehicleId();
    try { return driverDto(drivers.saveAndFlush(d)); }
    catch (DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_TAKEN", "Vehicle is already assigned to a driver"); }
  }
  @Transactional
  public DriverDto setDriverStatus(UUID id, DriverStatus s) {
    var d = drivers.findByIdAndOrganizationId(id, TenantContext.organizationId()).orElseThrow(() -> notFound("Driver"));
    return applyDriverStatus(d, s);
  }
  /** Offline drivers leave the GEO index; becoming unavailable emits driver.unavailable so waiting orders are reassigned. */
  private DriverDto applyDriverStatus(Driver d, DriverStatus s) {
    DriverStatus from = d.status; d.status = s;
    if (s == DriverStatus.OFFLINE) geo.removeQuietly(d.organizationId, d.id);
    boolean wasOut = from == DriverStatus.OFFLINE || from == DriverStatus.ON_BREAK;
    if ((s == DriverStatus.OFFLINE || s == DriverStatus.ON_BREAK) && !wasOut)
      outbox.enqueue(d.organizationId, d.id, "DRIVER_UNAVAILABLE", Topics.DRIVER_UNAVAILABLE, d.id.toString(), Map.of("driverId", d.id, "reason", s.name()));
    return driverDto(d);
  }
  // ---- driver self-service (role DRIVER) ----
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.dispatch.SettingsService settings;
  private static final Set<OrderStatus> ACTIVE_ORDER = EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.ARRIVED, OrderStatus.IN_TRANSIT);
  public record DriverHome(DriverDto driver, VehicleDto vehicle, List<OrderDto> deliveries) {}
  private Driver ownDriver(AuthPrincipal p) {
    return drivers.findByUserIdAndOrganizationId(p.userId(), p.organizationId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_DRIVER_PROFILE", "Your account is not linked to a driver profile. Ask your dispatcher."));
  }
  @Transactional(readOnly = true) public UUID ownDriverId() { return ownDriver(TenantContext.current()).id; }
  @Transactional(readOnly = true) public DriverHome driverHome() {
    var p = TenantContext.current(); var d = ownDriver(p);
    var v = d.vehicleId == null ? null : vehicles.findByIdAndOrganizationId(d.vehicleId, p.organizationId()).map(x -> vehicleDto(x, d.id)).orElse(null);
    return new DriverHome(driverDto(d), v, listOrders(null, null, null, null, null, d.id, 0, 30, "createdAt").content());
  }
  /** Drivers may go AVAILABLE / ON_BREAK / OFFLINE, but only when that is consistent with their active deliveries. BUSY is system-managed. */
  @Transactional public DriverDto setOwnStatus(DriverStatus s) {
    var p = TenantContext.current(); var own = ownDriver(p);
    if (s != DriverStatus.AVAILABLE && s != DriverStatus.OFFLINE && s != DriverStatus.ON_BREAK) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Choose available, on break or offline");
    var d = drivers.lockByIdAndOrg(own.id, p.organizationId()).orElseThrow(() -> notFound("Driver"));
    long active = orders.countByDriverIdAndStatusIn(d.id, ACTIVE_ORDER);
    if (s != DriverStatus.AVAILABLE && active > 0) throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_DELIVERIES", "Finish your active deliveries before going offline or on break");
    if (s == DriverStatus.AVAILABLE && active >= settings.get(p.organizationId()).maxWorkload()) throw new ApiException(HttpStatus.CONFLICT, "AT_CAPACITY", "You are at your delivery limit");
    return applyDriverStatus(d, s);
  }
  /** A driver may only move their OWN orders, and only through the field-work statuses (never CANCELLED). Other drivers' orders look like 404. */
  @Transactional public OrderDto changeOwnOrderStatus(UUID orderId, OrderStatus to) {
    var p = TenantContext.current(); var own = ownDriver(p);
    var o = orders.findByIdAndOrganizationId(orderId, p.organizationId()).filter(x -> own.id.equals(x.driverId)).orElseThrow(() -> notFound("Order"));
    if (!EnumSet.of(OrderStatus.ARRIVED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED, OrderStatus.FAILED).contains(to)) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Drivers cannot set that status");
    return applyTransition(o, to, p);
  }
  @Transactional(readOnly = true)
  public PageDto<DriverDto> listDrivers(int page, int size) { return PageDto.of(drivers.findByOrganizationId(TenantContext.organizationId(), page(page, size, "createdAt")).map(this::driverDto)); }
  private DriverDto driverDto(Driver d) { return new DriverDto(d.id, d.name, d.phone, d.status, d.vehicleId, d.rating, d.lastLat, d.lastLng, d.userId != null); }

  // ---- orders ----
  private OrderDto orderDto(DeliveryOrder o) {
    List<Item> items;
    try { items = om.readValue(o.items, new TypeReference<>() {}); } catch (Exception e) { items = List.of(); }
    return new OrderDto(o.id, o.customerName, o.customerPhone, o.pickupAddress, o.pickupLat, o.pickupLng, o.dropAddress, o.dropLat, o.dropLng,
        items, o.priority, o.vehicleRequirement, o.scheduledAt, o.status, o.driverId, o.createdAt, o.updatedAt);
  }
  /** Order row + timeline row + outbox row commit atomically. Kafka is never touched inside the transaction. */
  @Transactional
  public OrderDto createOrder(OrderRequest r) {
    var p = TenantContext.current();
    var o = new DeliveryOrder();
    o.organizationId = p.organizationId(); o.customerName = r.customerName().trim(); o.customerPhone = r.customerPhone();
    o.pickupAddress = r.pickupAddress(); o.pickupLat = r.pickupLat(); o.pickupLng = r.pickupLng();
    o.dropAddress = r.dropAddress(); o.dropLat = r.dropLat(); o.dropLng = r.dropLng();
    try { o.items = om.writeValueAsString(r.items()); } catch (Exception e) { throw new IllegalStateException(e); }
    o.priority = r.priority(); o.vehicleRequirement = r.vehicleRequirement(); o.scheduledAt = r.scheduledAt();
    orders.save(o); metrics.ordersCreated.increment();
    var payload = new LinkedHashMap<String, Object>();
    payload.put("orderId", o.id); payload.put("priority", o.priority); payload.put("vehicleRequirement", o.vehicleRequirement);
    payload.put("pickupLat", o.pickupLat); payload.put("pickupLng", o.pickupLng); payload.put("dropLat", o.dropLat); payload.put("dropLng", o.dropLng);
    UUID eventId = outbox.enqueue(o.organizationId, o.id, "ORDER_CREATED", Topics.ORDER_CREATED, o.id.toString(), payload);
    timeline(o, null, OrderStatus.CREATED, p.userId(), eventId);
    return orderDto(o);
  }
  private void timeline(DeliveryOrder o, OrderStatus from, OrderStatus to, UUID actor, UUID eventId) {
    var e = new OrderEvent(); e.organizationId = o.organizationId; e.orderId = o.id; e.fromStatus = from; e.toStatus = to; e.actorUserId = actor; e.eventId = eventId;
    orderEvents.save(e);
  }
  @Transactional
  public OrderDto changeStatus(UUID id, OrderStatus to) {
    var p = TenantContext.current();
    var o = orders.findByIdAndOrganizationId(id, p.organizationId()).orElseThrow(() -> notFound("Order"));
    return applyTransition(o, to, p);
  }
  private OrderDto applyTransition(DeliveryOrder o, OrderStatus to, AuthPrincipal p) {
    if (!MANUAL.contains(to)) throw new ApiException(HttpStatus.BAD_REQUEST, "SYSTEM_CONTROLLED", to + " is set by the dispatch engine");
    OrderStateMachine.require(o.status, to);
    OrderStatus from = o.status; o.status = to; o.updatedAt = Instant.now();
    if (o.driverId != null && (to == OrderStatus.DELIVERED || to == OrderStatus.CANCELLED || to == OrderStatus.FAILED))
      drivers.lockByIdAndOrg(o.driverId, o.organizationId).ifPresent(d -> { if (d.status == DriverStatus.BUSY) d.status = DriverStatus.AVAILABLE; });
    // topics are fixed by the spec, so ARRIVED and FAILED ride on the nearest lifecycle topic with a distinct eventType
    String[] m = switch (to) {
      case ARRIVED -> new String[]{"DRIVER_ARRIVED", Topics.DELIVERY_STARTED};
      case IN_TRANSIT -> new String[]{"DELIVERY_STARTED", Topics.DELIVERY_STARTED};
      case DELIVERED -> new String[]{"DELIVERY_COMPLETED", Topics.DELIVERY_COMPLETED};
      case CANCELLED -> new String[]{"DELIVERY_CANCELLED", Topics.DELIVERY_CANCELLED};
      default -> new String[]{"DELIVERY_FAILED", Topics.DELIVERY_CANCELLED};
    };
    UUID eventId = outbox.enqueue(o.organizationId, o.id, m[0], m[1], o.id.toString(), Map.of("orderId", o.id, "from", from, "to", to));
    timeline(o, from, to, p.userId(), eventId);
    if (to == OrderStatus.DELIVERED) metrics.ordersCompleted.increment(); else if (to == OrderStatus.FAILED) metrics.ordersFailed.increment();
    return orderDto(o);
  }
  @Transactional(readOnly = true)
  public OrderDetail getOrder(UUID id) {
    UUID org = TenantContext.organizationId();
    var o = orders.findByIdAndOrganizationId(id, org).orElseThrow(() -> notFound("Order"));
    var tl = orderEvents.findByOrderIdAndOrganizationIdOrderByCreatedAtAsc(id, org).stream()
        .map(e -> new TimelineEntry(e.fromStatus, e.toStatus, e.actorUserId, e.eventId, e.createdAt)).toList();
    return new OrderDetail(orderDto(o), tl);
  }
  @Transactional(readOnly = true)
  public PageDto<OrderDto> listOrders(OrderStatus status, Priority priority, String q, Instant from, Instant to, UUID driverId, int page, int size, String sort) {
    UUID org = TenantContext.organizationId();
    Specification<DeliveryOrder> spec = (root, cq, cb) -> cb.equal(root.get("organizationId"), org);
    if (status != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("status"), status));
    if (priority != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("priority"), priority));
    if (q != null && !q.isBlank()) { String like = "%" + q.trim().toLowerCase().replace("%", "").replace("_", "") + "%"; spec = spec.and((r, cq, cb) -> cb.like(cb.lower(r.get("customerName")), like)); }
    if (driverId != null) spec = spec.and((r, cq, cb) -> cb.equal(r.get("driverId"), driverId));
    if (from != null) spec = spec.and((r, cq, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from));
    if (to != null) spec = spec.and((r, cq, cb) -> cb.lessThan(r.get("createdAt"), to));
    return PageDto.of(orders.findAll(spec, page(page, size, sort)).map(this::orderDto));
  }
}

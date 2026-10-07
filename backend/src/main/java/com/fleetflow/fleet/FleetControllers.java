package com.fleetflow.fleet;
import com.fleetflow.fleet.Enums.*;
import com.fleetflow.fleet.FleetDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
public class FleetControllers {
  static final String READ = "hasAnyRole('OWNER','ADMIN','DISPATCHER')", WRITE = "hasAnyRole('OWNER','ADMIN')";

  @RestController @RequestMapping("/api/v1/vehicles")
  public static class Vehicles {
    private final FleetService s; public Vehicles(FleetService s) { this.s = s; }
    @GetMapping @PreAuthorize(READ) public PageDto<VehicleDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return s.listVehicles(page, size); }
    @PostMapping @PreAuthorize(WRITE) public ResponseEntity<VehicleDto> create(@Valid @RequestBody VehicleRequest r) { return ResponseEntity.status(HttpStatus.CREATED).body(s.createVehicle(r)); }
    @PutMapping("/{id}") @PreAuthorize(WRITE) public VehicleDto update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest r) { return s.updateVehicle(id, r); }
  }
  @RestController @RequestMapping("/api/v1/drivers")
  public static class Drivers {
    private final FleetService s; public Drivers(FleetService s) { this.s = s; }
    @GetMapping @PreAuthorize(READ) public PageDto<DriverDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return s.listDrivers(page, size); }
    @PostMapping @PreAuthorize(WRITE) public ResponseEntity<DriverDto> create(@Valid @RequestBody DriverRequest r) { return ResponseEntity.status(HttpStatus.CREATED).body(s.createDriver(r)); }
    @PutMapping("/{id}") @PreAuthorize(WRITE) public DriverDto update(@PathVariable UUID id, @Valid @RequestBody DriverRequest r) { return s.updateDriver(id, r); }
    @PatchMapping("/{id}/status") @PreAuthorize(READ) public DriverDto status(@PathVariable UUID id, @Valid @RequestBody DriverStatusRequest r) { return s.setDriverStatus(id, r.status()); }
  }
  @RestController @RequestMapping("/api/v1/orders")
  public static class Orders {
    private final FleetService s; public Orders(FleetService s) { this.s = s; }
    @GetMapping @PreAuthorize(READ) public PageDto<OrderDto> list(@RequestParam(required = false) OrderStatus status, @RequestParam(required = false) Priority priority,
        @RequestParam(required = false) String q, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) UUID driverId,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "createdAt") String sort) {
      return s.listOrders(status, priority, q, from, to, driverId, page, size, sort);
    }
    @PostMapping @PreAuthorize(READ) public ResponseEntity<OrderDto> create(@Valid @RequestBody OrderRequest r) { return ResponseEntity.status(HttpStatus.CREATED).body(s.createOrder(r)); }
    @GetMapping("/{id}") @PreAuthorize(READ) public OrderDetail get(@PathVariable UUID id) { return s.getOrder(id); }
    @PostMapping("/{id}/status") @PreAuthorize(READ) public OrderDto status(@PathVariable UUID id, @Valid @RequestBody StatusRequest r) { return s.changeStatus(id, r.status()); }
  }
}

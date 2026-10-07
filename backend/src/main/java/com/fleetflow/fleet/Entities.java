package com.fleetflow.fleet;
import com.fleetflow.fleet.Enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
public class Entities {
  @Entity @Table(name = "vehicles") public static class Vehicle {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "organization_id") public UUID organizationId;
    @Column(name = "registration_number") public String registrationNumber;
    @Enumerated(EnumType.STRING) public VehicleType type;
    @Column(name = "capacity_kg") public int capacityKg;
    @Enumerated(EnumType.STRING) public VehicleStatus status = VehicleStatus.ACTIVE;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
  }
  @Entity @Table(name = "drivers") public static class Driver {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "organization_id") public UUID organizationId;
    @Column(name = "user_id") public UUID userId;
    @Column(name = "vehicle_id") public UUID vehicleId;
    public String name; public String phone;
    @Enumerated(EnumType.STRING) public DriverStatus status = DriverStatus.OFFLINE;
    public java.math.BigDecimal rating = new java.math.BigDecimal("4.50");
    @Column(name = "last_lat") public Double lastLat;
    @Column(name = "last_lng") public Double lastLng;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
  }
  @Entity @Table(name = "orders") public static class DeliveryOrder {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "organization_id") public UUID organizationId;
    @Column(name = "customer_name") public String customerName;
    @Column(name = "customer_phone") public String customerPhone;
    @Column(name = "pickup_address") public String pickupAddress;
    @Column(name = "pickup_lat") public double pickupLat;
    @Column(name = "pickup_lng") public double pickupLng;
    @Column(name = "drop_address") public String dropAddress;
    @Column(name = "drop_lat") public double dropLat;
    @Column(name = "drop_lng") public double dropLng;
    public String items;
    @Enumerated(EnumType.STRING) public Priority priority;
    @Column(name = "vehicle_requirement") @Enumerated(EnumType.STRING) public VehicleType vehicleRequirement;
    @Column(name = "scheduled_at") public Instant scheduledAt;
    @Enumerated(EnumType.STRING) public OrderStatus status = OrderStatus.CREATED;
    @Column(name = "driver_id") public UUID driverId;
    @Column(name = "dispatch_attempts") public int dispatchAttempts;
    @Version public Long version;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
    @Column(name = "updated_at") public Instant updatedAt = Instant.now();
  }
  @Entity @Table(name = "order_events") public static class OrderEvent {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "organization_id") public UUID organizationId;
    @Column(name = "order_id") public UUID orderId;
    @Column(name = "from_status") @Enumerated(EnumType.STRING) public OrderStatus fromStatus;
    @Column(name = "to_status") @Enumerated(EnumType.STRING) public OrderStatus toStatus;
    @Column(name = "actor_user_id") public UUID actorUserId;
    @Column(name = "event_id") public UUID eventId;
    @Column(name = "created_at") public Instant createdAt = Instant.now();
  }
}

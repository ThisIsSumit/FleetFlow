package com.fleetflow.fleet;
import com.fleetflow.fleet.Enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public class FleetDtos {
  public record VehicleRequest(@NotBlank @Size(max = 32) String registrationNumber, @NotNull VehicleType type, @Min(1) int capacityKg, VehicleStatus status) {}
  public record VehicleDto(UUID id, String registrationNumber, VehicleType type, int capacityKg, VehicleStatus status, UUID assignedDriverId) {}
  public record DriverRequest(@NotBlank @Size(max = 120) String name, @Size(max = 20) String phone, UUID vehicleId) {}
  public record DriverStatusRequest(@NotNull DriverStatus status) {}
  public record DriverDto(UUID id, String name, String phone, DriverStatus status, UUID vehicleId, BigDecimal rating, Double lastLat, Double lastLng, boolean hasLogin) {}
  public record Item(@NotBlank @Size(max = 120) String name, @Min(1) int quantity) {}
  public record OrderRequest(@NotBlank @Size(max = 120) String customerName, @Size(max = 20) String customerPhone,
      @NotBlank @Size(max = 300) String pickupAddress, @NotNull @DecimalMin("-90") @DecimalMax("90") Double pickupLat, @NotNull @DecimalMin("-180") @DecimalMax("180") Double pickupLng,
      @NotBlank @Size(max = 300) String dropAddress, @NotNull @DecimalMin("-90") @DecimalMax("90") Double dropLat, @NotNull @DecimalMin("-180") @DecimalMax("180") Double dropLng,
      @NotEmpty @Size(max = 50) List<@Valid Item> items, @NotNull Priority priority, VehicleType vehicleRequirement, Instant scheduledAt) {}
  public record StatusRequest(@NotNull OrderStatus status) {}
  public record OrderDto(UUID id, String customerName, String customerPhone, String pickupAddress, double pickupLat, double pickupLng,
      String dropAddress, double dropLat, double dropLng, List<Item> items, Priority priority, VehicleType vehicleRequirement,
      Instant scheduledAt, OrderStatus status, UUID driverId, Instant createdAt, Instant updatedAt) {}
  public record TimelineEntry(OrderStatus from, OrderStatus to, UUID actorUserId, UUID eventId, Instant at) {}
  public record OrderDetail(OrderDto order, List<TimelineEntry> timeline) {}
  public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageDto<T> of(org.springframework.data.domain.Page<T> p) { return new PageDto<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages()); }
  }
}

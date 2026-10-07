package com.fleetflow.fleet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.fleetflow.fleet.Entities.*;
import com.fleetflow.fleet.Entities.DeliveryOrder;
import com.fleetflow.fleet.Entities.Driver;
import com.fleetflow.fleet.Entities.OrderEvent;
import com.fleetflow.fleet.Entities.Vehicle;
public class FleetRepos {
  public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    Optional<Vehicle> findByIdAndOrganizationId(UUID id, UUID org);
    Page<Vehicle> findByOrganizationId(UUID org, Pageable p);
    boolean existsByOrganizationIdAndRegistrationNumberIgnoreCase(UUID org, String reg);
  }
  public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByIdAndOrganizationId(UUID id, UUID org);
    Page<Driver> findByOrganizationId(UUID org, Pageable p);
    Optional<Driver> findByUserIdAndOrganizationId(UUID userId, UUID org);
    List<Driver> findByOrganizationIdAndVehicleIdIn(UUID org, Collection<UUID> vehicleIds);
    /** Row lock: serialises every assignment to one driver at the database level. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "select * from drivers where id = :id and organization_id = :org for update", nativeQuery = true)
    Optional<Driver> lockByIdAndOrg(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("org") UUID org);
  }
  public interface OrderRepository extends JpaRepository<DeliveryOrder, UUID>, JpaSpecificationExecutor<DeliveryOrder> {
    Optional<DeliveryOrder> findByIdAndOrganizationId(UUID id, UUID org);
    List<DeliveryOrder> findByDriverIdAndOrganizationIdAndStatusIn(UUID driverId, UUID org, Collection<Enums.OrderStatus> statuses);
    long countByDriverIdAndStatusIn(UUID driverId, Collection<Enums.OrderStatus> statuses);
    List<DeliveryOrder> findTop100ByStatusAndUpdatedAtBefore(Enums.OrderStatus status, java.time.Instant before);
  }
  public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {
    List<OrderEvent> findByOrderIdAndOrganizationIdOrderByCreatedAtAsc(UUID orderId, UUID org);
  }
}

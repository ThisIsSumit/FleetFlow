package com.fleetflow.dispatch;
import com.fleetflow.dispatch.DispatchScorer.Weights;
import jakarta.persistence.*;
import java.util.UUID;
public class DispatchSettings {
  public record Values(double radiusKm, int maxWorkload, Weights weights, boolean autoDispatch) {
    public static Values defaults() { return new Values(5, 1, new Weights(0.40, 0.20, 0.15, 0.15, 0.10), true); }
  }
  @Entity @Table(name = "dispatch_settings") public static class Row {
    @Id @Column(name = "organization_id") public UUID organizationId;
    @Column(name = "search_radius_km") public double radiusKm;
    @Column(name = "max_workload") public int maxWorkload;
    @Column(name = "w_distance") public double wDistance;
    @Column(name = "w_availability") public double wAvailability;
    @Column(name = "w_workload") public double wWorkload;
    @Column(name = "w_vehicle") public double wVehicle;
    @Column(name = "w_rating") public double wRating;
    @Column(name = "auto_dispatch") public boolean autoDispatch;
    public Values values() { return new Values(radiusKm, maxWorkload, new Weights(wDistance, wAvailability, wWorkload, wVehicle, wRating), autoDispatch); }
    public void set(Values v) {
      radiusKm = v.radiusKm(); maxWorkload = v.maxWorkload(); autoDispatch = v.autoDispatch();
      wDistance = v.weights().distance(); wAvailability = v.weights().availability(); wWorkload = v.weights().workload(); wVehicle = v.weights().vehicle(); wRating = v.weights().rating();
    }
  }
  public interface Repo extends org.springframework.data.jpa.repository.JpaRepository<Row, UUID> {}
}

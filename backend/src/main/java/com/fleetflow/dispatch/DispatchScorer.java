package com.fleetflow.dispatch;
import com.fleetflow.fleet.Enums.VehicleType;
import java.util.*;
/** Deterministic weighted scoring. Not machine learning: same inputs always give the same ranking. */
public final class DispatchScorer {
  private DispatchScorer() {}
  public record Weights(double distance, double availability, double workload, double vehicle, double rating) {
    public double sum() { return distance + availability + workload + vehicle + rating; }
    public Weights normalized() { double s = sum(); return new Weights(distance / s, availability / s, workload / s, vehicle / s, rating / s); }
  }
  public record Candidate(UUID driverId, double distanceKm, int activeOrders, boolean available, VehicleType vehicle, double rating) {}
  public record Scored(UUID driverId, double score) {}

  /** @return 1.0 exact, 0.6 larger than needed, 0.5 no vehicle when none required, -1 incompatible */
  public static double vehicleMatch(VehicleType required, VehicleType actual) {
    if (required == null) return actual == null ? 0.5 : 1.0;
    if (actual == null) return -1;
    if (actual == required) return 1.0;
    return actual.ordinal() > required.ordinal() ? 0.6 : -1;
  }
  public static List<Scored> rank(List<Candidate> candidates, Weights raw, double radiusKm, int maxWorkload, VehicleType required) {
    Weights w = raw.normalized();
    var scored = new ArrayList<Map.Entry<Candidate, Double>>();
    for (var c : candidates) {
      double vm = vehicleMatch(required, c.vehicle());
      if (vm < 0 || c.activeOrders() >= maxWorkload || c.distanceKm() > radiusKm) continue;
      double distance = clamp(1 - c.distanceKm() / radiusKm);
      double availability = c.available() ? 1 : 0;
      double workload = clamp(1 - (double) c.activeOrders() / maxWorkload);
      double rating = clamp(c.rating() / 5.0);
      double score = distance * w.distance() + availability * w.availability() + workload * w.workload() + vm * w.vehicle() + rating * w.rating();
      scored.add(Map.entry(c, score));
    }
    scored.sort(Comparator.<Map.Entry<Candidate, Double>>comparingDouble(Map.Entry::getValue).reversed()
        .thenComparingDouble(e -> e.getKey().distanceKm()).thenComparing(e -> e.getKey().driverId()));
    return scored.stream().map(e -> new Scored(e.getKey().driverId(), e.getValue())).toList();
  }
  private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}

package com.fleetflow.dispatch;
import com.fleetflow.dispatch.DispatchScorer.*;
import com.fleetflow.fleet.Enums.VehicleType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DispatchScorerTest {
  static final Weights W = new Weights(0.40, 0.20, 0.15, 0.15, 0.10);
  static Candidate c(double km, int active, VehicleType v, double rating) { return new Candidate(UUID.randomUUID(), km, active, true, v, rating); }
  @Test void nearerDriverWinsWhenOtherwiseEqual() {
    var near = c(1, 0, VehicleType.BIKE, 4.5); var far = c(4, 0, VehicleType.BIKE, 4.5);
    assertEquals(near.driverId(), DispatchScorer.rank(List.of(far, near), W, 5, 3, VehicleType.BIKE).get(0).driverId());
  }
  @Test void incompatibleVehicleAndOverloadedAndOutOfRadiusAreExcluded() {
    var small = c(1, 0, VehicleType.BIKE, 5); var full = c(1, 3, VehicleType.VAN, 5); var out = c(6, 0, VehicleType.VAN, 5); var ok = c(2, 0, VehicleType.VAN, 4);
    var r = DispatchScorer.rank(List.of(small, full, out, ok), W, 5, 3, VehicleType.VAN);
    assertEquals(1, r.size()); assertEquals(ok.driverId(), r.get(0).driverId());
  }
  @Test void exactVehicleBeatsOversizedAtSameDistance() {
    var exact = c(2, 0, VehicleType.CAR, 4.5); var big = c(2, 0, VehicleType.TRUCK, 4.5);
    assertEquals(exact.driverId(), DispatchScorer.rank(List.of(big, exact), W, 5, 3, VehicleType.CAR).get(0).driverId());
  }
  @Test void weightsChangeTheOutcome() {
    var closeLowRated = c(0.5, 0, VehicleType.CAR, 2.0); var farTopRated = c(3, 0, VehicleType.CAR, 5.0);
    assertEquals(closeLowRated.driverId(), DispatchScorer.rank(List.of(farTopRated, closeLowRated), W, 5, 3, null).get(0).driverId());
    var ratingHeavy = new Weights(0.05, 0.05, 0.05, 0.05, 0.80);
    assertEquals(farTopRated.driverId(), DispatchScorer.rank(List.of(farTopRated, closeLowRated), ratingHeavy, 5, 3, null).get(0).driverId());
  }
  @Test void rankingIsDeterministic() {
    var list = new ArrayList<Candidate>(); for (int i = 0; i < 20; i++) list.add(c(1 + i * 0.1, i % 2, VehicleType.CAR, 3 + (i % 3)));
    var a = DispatchScorer.rank(list, W, 5, 3, null); Collections.shuffle(list, new Random(7)); var b = DispatchScorer.rank(list, W, 5, 3, null);
    assertEquals(a, b);
  }
  @Test void weightsAreNormalised() { assertEquals(1.0, new Weights(4, 2, 1.5, 1.5, 1).normalized().sum(), 1e-9); }
}

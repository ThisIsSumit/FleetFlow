package com.fleetflow.dispatch;
import com.fleetflow.common.ApiException;
import com.fleetflow.dispatch.DispatchScorer.Weights;
import com.fleetflow.dispatch.DispatchSettings.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
@Service
public class SettingsService {
  private final Repo repo; private final com.fleetflow.common.CacheService cache;
  public SettingsService(Repo repo, com.fleetflow.common.CacheService cache) { this.repo = repo; this.cache = cache; }
  /** Read on every dispatch and assignment, changed rarely: cached in Redis for 60 s and evicted after the saving transaction commits. */
  @Transactional(readOnly = true) public Values get(UUID org) {
    return cache.get("dispatch-settings", org.toString(), Values.class, java.time.Duration.ofSeconds(60), () -> repo.findById(org).map(Row::values).orElseGet(Values::defaults));
  }
  @Transactional public Values save(UUID org, Values v) {
    Weights w = v.weights();
    if (Math.min(Math.min(w.distance(), w.availability()), Math.min(Math.min(w.workload(), w.vehicle()), w.rating())) < 0 || w.sum() <= 0)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEIGHTS", "Weights must be non-negative with a positive sum");
    if (v.radiusKm() <= 0 || v.radiusKm() > 50 || v.maxWorkload() < 1 || v.maxWorkload() > 10)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS", "radiusKm must be in (0,50], maxWorkload in [1,10]");
    var row = repo.findById(org).orElseGet(() -> { var r = new Row(); r.organizationId = org; return r; });
    row.set(v); repo.save(row);
    Runnable evict = () -> cache.evict("dispatch-settings", org.toString());
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() { @Override public void afterCommit() { evict.run(); } });
    else evict.run();
    return row.values();
  }
}

package com.fleetflow.dispatch;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;
/**
 * Redis lock in front, database lock behind. The Redis lock cuts contention (losers queue in Redis instead of on row locks);
 * the DB row lock is what guarantees correctness, so a Redis outage degrades throughput, never correctness.
 * Known trade-off: a lock holder that stalls past the 10s TTL loses the lock; the DB layer still prevents a double assignment.
 */
@Service
public class AssignmentService {
  private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);
  private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
      "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
  private final StringRedisTemplate redis; private final DispatchTx tx;
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.ops.FaultInjector faults;
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.ops.FleetMetrics metrics;
  public AssignmentService(StringRedisTemplate redis, DispatchTx tx) { this.redis = redis; this.tx = tx; }

  public boolean tryAssign(UUID org, UUID orderId, UUID driverId) {
    String key = "lock:driver:" + driverId, token = UUID.randomUUID().toString();
    boolean locked = false, redisUp = true;
    try {
      faults.maybeFail(com.fleetflow.ops.FaultInjector.Fault.REDIS_UNAVAILABLE); // simulated outage falls into the DB-only path below
      long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
      while (!(locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(10)))) && System.nanoTime() < deadline) Thread.sleep(15);
    } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false;
    } catch (Exception e) { redisUp = false; log.warn("Redis lock unavailable, relying on DB row lock only: {}", e.toString()); }
    if (redisUp && !locked) return false; // could not get the lock in time: try another candidate
    try {
      return metrics.assignmentLatency.record(() -> tx.assign(org, orderId, driverId));
    } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException | CannotAcquireLockException e) {
      log.info("Assignment lost a race for order {}: {}", orderId, e.getClass().getSimpleName()); return false;
    } finally {
      if (locked) try { redis.execute(RELEASE, List.of(key), token); } catch (Exception e) { log.warn("Lock release failed (TTL will expire it): {}", e.toString()); }
    }
  }
}

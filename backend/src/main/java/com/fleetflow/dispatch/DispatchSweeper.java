package com.fleetflow.dispatch;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
/** Orders that found no driver stay in DISPATCH_REQUESTED; this re-emits them (every ~30 s) so newly available drivers get a chance.
 *  After max-attempts (default 20, about 10 minutes) the order is failed and a SYSTEM_ALERT is raised instead of retrying forever.
 *  Organizations with auto-dispatch off are skipped: their orders wait for a dispatcher.
 *  A transaction-level advisory lock lets only one instance sweep at a time (otherwise every instance would re-emit and burn attempts). */
@Component
public class DispatchSweeper {
  private static final Logger log = LoggerFactory.getLogger(DispatchSweeper.class);
  static final long LOCK_KEY = 7332;
  private final DispatchTx tx; private final JdbcTemplate jdbc; private final TransactionTemplate txTemplate; private final long afterSeconds; private final int maxAttempts;
  public DispatchSweeper(DispatchTx tx, JdbcTemplate jdbc, TransactionTemplate txTemplate, @Value("${fleetflow.dispatch.sweep-after-seconds}") long afterSeconds, @Value("${fleetflow.dispatch.max-attempts}") int maxAttempts) {
    this.tx = tx; this.jdbc = jdbc; this.txTemplate = txTemplate; this.afterSeconds = afterSeconds; this.maxAttempts = maxAttempts;
  }
  @Scheduled(fixedDelayString = "15000")
  public void sweep() {
    txTemplate.executeWithoutResult(status -> {
      if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(" + LOCK_KEY + ")", Boolean.class))) return; // another instance is sweeping
      for (var id : tx.stuckOrderIds(Instant.now().minusSeconds(afterSeconds)))
        try { tx.resweepOne(id, maxAttempts); } catch (Exception e) { log.info("Sweep skipped order {}: {}", id, e.getClass().getSimpleName()); } // REQUIRES_NEW: one order per transaction
    });
  }
}

package com.fleetflow.dispatch;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
/** Orders that found no driver stay in DISPATCH_REQUESTED; this re-emits them (every ~30 s) so newly available drivers get a chance.
 *  After max-attempts (default 20, about 10 minutes) the order is failed and a SYSTEM_ALERT is raised instead of retrying forever. */
@Component
public class DispatchSweeper {
  private static final Logger log = LoggerFactory.getLogger(DispatchSweeper.class);
  private final DispatchTx tx; private final long afterSeconds; private final int maxAttempts;
  public DispatchSweeper(DispatchTx tx, @Value("${fleetflow.dispatch.sweep-after-seconds}") long afterSeconds, @Value("${fleetflow.dispatch.max-attempts}") int maxAttempts) { this.tx = tx; this.afterSeconds = afterSeconds; this.maxAttempts = maxAttempts; }
  @Scheduled(fixedDelayString = "15000")
  public void sweep() {
    for (var id : tx.stuckOrderIds(Instant.now().minusSeconds(afterSeconds)))
      try { tx.resweepOne(id, maxAttempts); } catch (Exception e) { log.info("Sweep skipped order {}: {}", id, e.getClass().getSimpleName()); }
  }
}

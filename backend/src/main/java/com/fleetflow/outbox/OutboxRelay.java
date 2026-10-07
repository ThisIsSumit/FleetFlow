package com.fleetflow.outbox;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
@Component
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  static final int MAX_ATTEMPTS = 10;
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.ops.FaultInjector faults;
  private final OutboxRepository repo; private final EventPublisher publisher;
  public OutboxRelay(OutboxRepository repo, EventPublisher publisher) { this.repo = repo; this.publisher = publisher; }

  static long backoffSeconds(int attempts) { return Math.min(300, 1L << Math.min(attempts, 10)); } // 2,4,8,... capped at 5 min

  /** At-least-once delivery: a crash after publish but before commit re-sends the row, hence idempotent consumers (slice 3). */
  @Scheduled(fixedDelayString = "${fleetflow.outbox.poll-ms:500}")
  @Transactional
  public void drain() {
    for (var e : repo.lockBatch(100)) {
      try {
        faults.maybeFail(com.fleetflow.ops.FaultInjector.Fault.KAFKA_UNAVAILABLE);
        publisher.publish(e.topic, e.partitionKey, e.payload);
        e.status = "SENT"; e.sentAt = Instant.now(); e.lastError = null;
      } catch (Exception ex) {
        e.attempts++;
        e.lastError = String.valueOf(ex.getMessage()).substring(0, Math.min(500, String.valueOf(ex.getMessage()).length()));
        if (e.attempts >= MAX_ATTEMPTS) { e.status = "FAILED"; log.error("Outbox event {} moved to FAILED", e.id); }
        else e.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds(e.attempts));
      }
    }
  }
}

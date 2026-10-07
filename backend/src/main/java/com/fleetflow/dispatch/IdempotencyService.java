package com.fleetflow.dispatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
/** processed_events (consumer, event_id) is the dedupe ledger; its primary key is the arbiter under concurrent duplicates. */
@Service
public class IdempotencyService {
  private final JdbcTemplate jdbc; public IdempotencyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public boolean seen(String consumer, UUID eventId) {
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM processed_events WHERE consumer=? AND event_id=?)", Boolean.class, consumer, eventId));
  }
  public boolean mark(String consumer, UUID eventId) {
    return jdbc.update("INSERT INTO processed_events(consumer,event_id) VALUES (?,?) ON CONFLICT DO NOTHING", consumer, eventId) == 1;
  }
  /** Effect and ledger row commit together: a failure rolls back both, so a redelivery re-runs the effect. */
  @Transactional
  public boolean runOnce(String consumer, UUID eventId, Runnable effect) {
    if (!mark(consumer, eventId)) return false;
    effect.run(); return true;
  }
}

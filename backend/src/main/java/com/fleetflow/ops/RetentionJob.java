package com.fleetflow.ops;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Keeps append-only tables bounded. processed_events retention must exceed Kafka retention (7 days by default) plus any DLQ replay window. */
@Component
public class RetentionJob {
  private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
  private static final int BATCH = 5000;
  private final JdbcTemplate jdbc; private final MeterRegistry reg;
  @Value("${fleetflow.retention.outbox-days:7}") private int outboxDays;
  @Value("${fleetflow.retention.processed-events-days:30}") private int ledgerDays;
  @Value("${fleetflow.retention.auth-token-days:7}") private int tokenDays;
  public RetentionJob(JdbcTemplate jdbc, MeterRegistry reg) { this.jdbc = jdbc; this.reg = reg; }
  private int loop(String sql, Object... args) { int total = 0, n; do { n = jdbc.update(sql, args); total += n; } while (n >= BATCH); return total; }
  /** A transaction-level advisory lock makes concurrent instances skip instead of fighting over the same rows. */
  @Scheduled(cron = "${fleetflow.retention.cron:0 15 3 * * *}") @Transactional
  public Map<String, Integer> prune() {
    if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(7331)", Boolean.class))) return Map.of();
    var out = new LinkedHashMap<String, Integer>();
    out.put("outbox_events", loop("DELETE FROM outbox_events WHERE id IN (SELECT id FROM outbox_events WHERE status = 'SENT' AND sent_at < now() - make_interval(days => ?) LIMIT " + BATCH + ")", outboxDays));
    out.put("processed_events", loop("DELETE FROM processed_events WHERE ctid IN (SELECT ctid FROM processed_events WHERE processed_at < now() - make_interval(days => ?) LIMIT " + BATCH + ")", ledgerDays));
    out.put("auth_tokens", loop("DELETE FROM auth_tokens WHERE id IN (SELECT id FROM auth_tokens WHERE (used_at IS NOT NULL AND used_at < now() - make_interval(days => ?)) OR expires_at < now() - make_interval(days => ?) LIMIT " + BATCH + ")", tokenDays, tokenDays));
    out.forEach((t, c) -> { if (c > 0) reg.counter("retention.deleted", "table", t).increment(c); });
    if (out.values().stream().anyMatch(c -> c > 0)) log.info("Retention pruned {}", out);
    return out;
  }
}

package com.fleetflow.ops;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Testcontainers
class RetentionIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername); r.add("spring.datasource.password", pg::getPassword);
    r.add("fleetflow.jwt.secret", () -> "test-secret-test-secret-test-secret-123456"); r.add("fleetflow.refresh.cookie-secure", () -> "false"); r.add("fleetflow.outbox.poll-ms", () -> "3600000");
  }
  @Autowired RetentionJob job; @Autowired JdbcTemplate jdbc;
  UUID outbox(String status, int ageDays) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO outbox_events(id, organization_id, aggregate_id, event_type, topic, partition_key, payload, status, created_at, sent_at) VALUES (?,?,?,?,?,?,?,?, now() - make_interval(days => ?), CASE WHEN ? = 'SENT' THEN now() - make_interval(days => ?) END)",
        id, UUID.randomUUID(), UUID.randomUUID(), "T", "t", "k", "{}", status, ageDays, status, ageDays);
    return id;
  }
  int exists(UUID id) { return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE id = ?", Integer.class, id); }
  @Test void prunesOnlyOldSentOutboxRowsAndOldLedgerEntries() {
    UUID oldSent = outbox("SENT", 10), freshSent = outbox("SENT", 1), oldPending = outbox("PENDING", 10);
    jdbc.update("INSERT INTO processed_events(consumer, event_id, processed_at) VALUES ('c', ?, now() - interval '40 days')", UUID.randomUUID());
    UUID recent = UUID.randomUUID(); jdbc.update("INSERT INTO processed_events(consumer, event_id) VALUES ('c', ?)", recent);
    var result = job.prune();
    assertEquals(0, exists(oldSent)); assertEquals(1, exists(freshSent)); assertEquals(1, exists(oldPending)); // never drops undelivered events
    assertTrue(result.get("processed_events") >= 1);
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Integer.class, recent));
  }
}

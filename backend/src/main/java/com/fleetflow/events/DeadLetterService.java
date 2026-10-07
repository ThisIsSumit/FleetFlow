package com.fleetflow.events;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.common.ApiException;
import com.fleetflow.outbox.EventPublisher;
import com.fleetflow.security.TenantContext;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
/** Persists DLQ messages so operators can inspect and replay them from the API. */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
class DeadLetterRecorder {
  private final JdbcTemplate jdbc; private final ObjectMapper om;
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.ops.FleetMetrics metrics;
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.notify.NotificationRequester notifications;
  DeadLetterRecorder(JdbcTemplate jdbc, ObjectMapper om) { this.jdbc = jdbc; this.om = om; }
  @KafkaListener(topicPattern = ".*\\.DLQ", groupId = "fleetflow-dlq")
  void record(ConsumerRecord<String, String> r) {
    String orig = r.topic().substring(0, r.topic().length() - 4);
    var h = r.headers().lastHeader("kafka_dlt-exception-message");
    String err = h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    UUID org = null;
    try { org = UUID.fromString(om.readTree(r.value()).get("organizationId").asText()); } catch (Exception ignored) {}
    jdbc.update("INSERT INTO dead_letter_events(id, organization_id, original_topic, partition_key, payload, error) VALUES (?,?,?,?,?,?)",
        UUID.randomUUID(), org, orig, r.key(), r.value(), err == null ? null : err.substring(0, Math.min(500, err.length())));
    metrics.dlq.increment();
    if (org != null) notifications.request(org, "SYSTEM_ALERT", "Event moved to the dead-letter queue", "An event on " + orig + " failed repeatedly and needs review.", null);
  }
}
@RestController @RequestMapping("/api/v1/admin/dead-letters")
class DeadLetterController {
  record Dead(UUID id, String originalTopic, String key, String error, Instant createdAt, Instant replayedAt, String payload) {}
  private final JdbcTemplate jdbc; private final org.springframework.beans.factory.ObjectProvider<EventPublisher> publisher;
  DeadLetterController(JdbcTemplate jdbc, org.springframework.beans.factory.ObjectProvider<EventPublisher> publisher) { this.jdbc = jdbc; this.publisher = publisher; }
  @GetMapping @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public List<Dead> list() {
    return jdbc.query("SELECT id, original_topic, partition_key, error, created_at, replayed_at, payload FROM dead_letter_events WHERE organization_id = ? ORDER BY created_at DESC LIMIT 100",
        (rs, i) -> new Dead(rs.getObject("id", UUID.class), rs.getString("original_topic"), rs.getString("partition_key"), rs.getString("error"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("replayed_at") == null ? null : rs.getTimestamp("replayed_at").toInstant(), rs.getString("payload")),
        TenantContext.organizationId());
  }
  /** Replays the original payload (same eventId) to the original topic. Idempotent consumers make this safe. */
  @PostMapping("/{id}/replay") @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
  public void replay(@PathVariable UUID id) throws Exception {
    var rows = jdbc.queryForList("SELECT original_topic, partition_key, payload FROM dead_letter_events WHERE id = ? AND organization_id = ?", id, TenantContext.organizationId());
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Dead letter not found");
    var r = rows.get(0);
    publisher.getObject().publish((String) r.get("original_topic"), (String) r.get("partition_key"), (String) r.get("payload"));
    jdbc.update("UPDATE dead_letter_events SET replayed_at = now() WHERE id = ?", id);
  }
}

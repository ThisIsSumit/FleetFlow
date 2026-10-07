package com.fleetflow.events;
import com.fasterxml.jackson.databind.*;
import com.fleetflow.dispatch.IdempotencyService;
import com.fleetflow.notify.*;
import com.fleetflow.outbox.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** Stage 1: domain events -> notification.requested (via outbox). Stage 2: notification.requested -> notifications table. */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
public class NotificationConsumers {
  private final ObjectMapper om; private final IdempotencyService idem; private final NotificationService notes; private final NotificationRequester requester; private final JdbcTemplate jdbc;
  public NotificationConsumers(ObjectMapper om, IdempotencyService idem, NotificationService notes, NotificationRequester requester, JdbcTemplate jdbc) { this.om = om; this.idem = idem; this.notes = notes; this.requester = requester; this.jdbc = jdbc; }

  @KafkaListener(topics = {Topics.DRIVER_ASSIGNED, Topics.DELIVERY_COMPLETED, Topics.DELIVERY_CANCELLED}, groupId = "fleetflow-notification-triggers")
  public void onDomainEvent(ConsumerRecord<String, String> rec) throws Exception {
    JsonNode n = om.readTree(rec.value());
    UUID eventId = UUID.fromString(n.get("eventId").asText()), org = UUID.fromString(n.get("organizationId").asText()), orderId = UUID.fromString(n.get("aggregateId").asText());
    String type = n.get("eventType").asText();
    idem.runOnce("notification-trigger", eventId, () -> {
      var r = jdbc.queryForList("SELECT o.customer_name, d.name AS driver FROM orders o LEFT JOIN drivers d ON d.id = o.driver_id WHERE o.id = ? AND o.organization_id = ?", orderId, org);
      if (r.isEmpty()) return;
      String customer = String.valueOf(r.get(0).get("customer_name")), driver = String.valueOf(r.get(0).get("driver"));
      switch (type) {
        case "DRIVER_ASSIGNED" -> requester.request(org, "DRIVER_ASSIGNED", "Driver assigned", driver + " will deliver the order for " + customer + ".", orderId);
        case "DELIVERY_COMPLETED" -> requester.request(org, "DELIVERY_COMPLETED", "Delivery completed", "Order for " + customer + " was delivered.", orderId);
        case "DELIVERY_CANCELLED" -> requester.request(org, "ORDER_CANCELLED", "Order cancelled", "Order for " + customer + " was cancelled.", orderId);
        default -> { /* DELIVERY_FAILED / DRIVER_ARRIVED share these topics but raise their own alerts elsewhere */ }
      }
    });
  }

  @KafkaListener(topics = Topics.NOTIFICATION, groupId = "fleetflow-notification-writer")
  public void onRequested(ConsumerRecord<String, String> rec) throws Exception {
    JsonNode n = om.readTree(rec.value()); JsonNode p = n.get("payload");
    UUID eventId = UUID.fromString(n.get("eventId").asText()), org = UUID.fromString(n.get("organizationId").asText());
    UUID orderId = p.hasNonNull("orderId") ? UUID.fromString(p.get("orderId").asText()) : null;
    idem.runOnce("notification-writer", eventId, () -> notes.create(org, p.get("type").asText(), p.get("title").asText(), p.get("message").asText(), orderId));
  }
}

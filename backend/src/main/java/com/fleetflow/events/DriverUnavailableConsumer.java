package com.fleetflow.events;
import com.fasterxml.jackson.databind.*;
import com.fleetflow.dispatch.*;
import com.fleetflow.outbox.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** driver.unavailable: orders not yet picked up go back to dispatch; orders already in progress raise an alert for a human. */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
public class DriverUnavailableConsumer {
  private static final Logger log = LoggerFactory.getLogger(DriverUnavailableConsumer.class);
  private final ObjectMapper om; private final IdempotencyService idem; private final DispatchTx tx;
  public DriverUnavailableConsumer(ObjectMapper om, IdempotencyService idem, DispatchTx tx) { this.om = om; this.idem = idem; this.tx = tx; }
  @KafkaListener(topics = Topics.DRIVER_UNAVAILABLE, groupId = "fleetflow-dispatch")
  public void on(ConsumerRecord<String, String> rec) throws Exception {
    JsonNode n = om.readTree(rec.value());
    UUID eventId = UUID.fromString(n.get("eventId").asText()), org = UUID.fromString(n.get("organizationId").asText()), driver = UUID.fromString(n.get("aggregateId").asText());
    idem.runOnce("driver-unavailable-handler", eventId, () -> { int[] r = tx.reassignFromDriver(org, driver); log.info("Driver {} unavailable: {} order(s) re-queued, {} in progress", driver, r[0], r[1]); });
  }
}

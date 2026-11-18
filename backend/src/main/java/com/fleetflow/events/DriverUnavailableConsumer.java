package com.fleetflow.events;
import com.fleetflow.outbox.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
/** driver.unavailable: orders not yet picked up go back to dispatch; orders already in progress raise an alert for a human. */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
public class DriverUnavailableConsumer {
  private final EventHandlers handlers;
  public DriverUnavailableConsumer(EventHandlers handlers) { this.handlers = handlers; }
  @KafkaListener(topics = Topics.DRIVER_UNAVAILABLE, groupId = "fleetflow-dispatch")
  public void on(ConsumerRecord<String, String> rec) { handlers.driverUnavailable(rec.value()); }
}

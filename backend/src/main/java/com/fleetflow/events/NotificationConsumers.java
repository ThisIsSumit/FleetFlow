package com.fleetflow.events;
import com.fleetflow.outbox.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
/** Stage 1: domain events -> notification.requested (via outbox). Stage 2: notification.requested -> notifications table. */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
public class NotificationConsumers {
  private final EventHandlers handlers;
  public NotificationConsumers(EventHandlers handlers) { this.handlers = handlers; }
  @KafkaListener(topics = {Topics.DRIVER_ASSIGNED, Topics.DELIVERY_COMPLETED, Topics.DELIVERY_CANCELLED}, groupId = "fleetflow-notification-triggers")
  public void onDomainEvent(ConsumerRecord<String, String> rec) { handlers.notificationTrigger(rec.value()); }
  @KafkaListener(topics = Topics.NOTIFICATION, groupId = "fleetflow-notification-writer")
  public void onRequested(ConsumerRecord<String, String> rec) { handlers.notificationRequested(rec.value()); }
}

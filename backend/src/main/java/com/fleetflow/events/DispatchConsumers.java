package com.fleetflow.events;
import com.fleetflow.outbox.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
/** Kafka transport for the dispatch pipeline; the logic lives in {@link EventHandlers}. */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
public class DispatchConsumers {
  private final EventHandlers handlers;
  public DispatchConsumers(EventHandlers handlers) { this.handlers = handlers; }
  @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "fleetflow-dispatch")
  public void onOrderCreated(ConsumerRecord<String, String> rec) { handlers.orderCreated(rec.value()); }
  @KafkaListener(topics = Topics.DISPATCH_REQUESTED, groupId = "fleetflow-dispatch")
  public void onDispatchRequested(ConsumerRecord<String, String> rec) { handlers.dispatchRequested(rec.value()); }
}

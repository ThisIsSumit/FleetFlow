package com.fleetflow.events;
import com.fleetflow.outbox.EventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;
/** Synchronous send: the outbox relay must know whether the broker acknowledged (acks=all) before marking a row SENT. */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisher {
  private final KafkaTemplate<String, String> kafka;
  public KafkaEventPublisher(KafkaTemplate<String, String> kafka) { this.kafka = kafka; }
  public void publish(String topic, String key, String json) throws Exception { kafka.send(topic, key, json).get(5, TimeUnit.SECONDS); }
}

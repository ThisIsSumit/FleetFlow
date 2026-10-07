package com.fleetflow.outbox;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/** Dev default until the Kafka publisher lands (fleetflow.events.publisher=kafka). */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "log", matchIfMissing = true)
public class LoggingEventPublisher implements EventPublisher {
  private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);
  public void publish(String topic, String key, String json) { log.info("EVENT topic={} key={} {}", topic, key, json); }
}

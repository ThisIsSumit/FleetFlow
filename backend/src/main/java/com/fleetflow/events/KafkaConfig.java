package com.fleetflow.events;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fleetflow.outbox.Topics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.ExponentialBackOff;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.core.KafkaAdmin;
import java.util.List;
@Configuration @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
public class KafkaConfig {
  static final List<String> TOPICS = List.of(Topics.ORDER_CREATED, Topics.DISPATCH_REQUESTED, Topics.DRIVER_ASSIGNED, Topics.DRIVER_LOCATION,
      Topics.DELIVERY_STARTED, Topics.DELIVERY_COMPLETED, Topics.DELIVERY_CANCELLED, Topics.DRIVER_UNAVAILABLE, Topics.NOTIFICATION);
  @Bean KafkaAdmin.NewTopics topics() {
    var all = new java.util.ArrayList<NewTopic>();
    for (String t : TOPICS) all.add(TopicBuilder.name(t).partitions(3).replicas(1).build()); // keyed by orderId: per-order ordering, cross-order parallelism
    // one DLQ per consumed topic (the error handler routes to "<topic>.DLQ"; relying on broker auto-create breaks when it is disabled)
    for (String t : List.of(Topics.ORDER_CREATED, Topics.DISPATCH_REQUESTED, Topics.DRIVER_UNAVAILABLE, Topics.NOTIFICATION,
        Topics.DRIVER_ASSIGNED, Topics.DELIVERY_COMPLETED, Topics.DELIVERY_CANCELLED)) all.add(TopicBuilder.name(t + ".DLQ").partitions(1).replicas(1).build());
    return new KafkaAdmin.NewTopics(all.toArray(NewTopic[]::new));
  }
  /**
   * Retry 3 times with exponential backoff (1s, 2s, 4s), then publish to "<topic>.DLQ".
   * Trade-off: retries block the partition (simple, preserves per-order ordering) instead of using non-blocking retry topics.
   * Malformed events are never retried: they go straight to the DLQ.
   */
  @Bean @SuppressWarnings({"unchecked", "rawtypes"})
  org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer consumerMetrics(io.micrometer.core.instrument.MeterRegistry reg) {
    return f -> ((org.springframework.kafka.core.DefaultKafkaConsumerFactory) f).addListener(new org.springframework.kafka.core.MicrometerConsumerListener(reg)); // exposes kafka_consumer_fetch_manager_records_lag*
  }
  @Bean DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template, (r, e) -> new TopicPartition(r.topic() + ".DLQ", -1));
    var backoff = new ExponentialBackOff(1000L, 2.0); backoff.setMaxAttempts(3);
    var h = new DefaultErrorHandler(recoverer, backoff);
    h.addNotRetryableExceptions(IllegalArgumentException.class, JsonProcessingException.class);
    return h;
  }
}

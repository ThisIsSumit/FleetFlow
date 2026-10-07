package com.fleetflow.events;
import com.fasterxml.jackson.databind.*;
import com.fleetflow.common.CorrelationFilter;
import com.fleetflow.dispatch.*;
import com.fleetflow.ops.*;
import com.fleetflow.outbox.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "kafka")
public class DispatchConsumers {
  private static final Logger log = LoggerFactory.getLogger(DispatchConsumers.class);
  record Envelope(UUID eventId, UUID orgId, UUID aggregateId, String correlationId) {}
  private final ObjectMapper om; private final IdempotencyService idem; private final DispatchTx tx; private final DispatchService dispatch;
  private final FaultInjector faults; private final FleetMetrics metrics;
  public DispatchConsumers(ObjectMapper om, IdempotencyService idem, DispatchTx tx, DispatchService dispatch, FaultInjector faults, FleetMetrics metrics) {
    this.om = om; this.idem = idem; this.tx = tx; this.dispatch = dispatch; this.faults = faults; this.metrics = metrics;
  }
  Envelope parse(String json) {
    try {
      JsonNode n = om.readTree(json);
      return new Envelope(UUID.fromString(n.get("eventId").asText()), UUID.fromString(n.get("organizationId").asText()), UUID.fromString(n.get("aggregateId").asText()), n.path("correlationId").asText(""));
    } catch (Exception e) { throw new IllegalArgumentException("Malformed event envelope", e); } // not retryable -> DLQ
  }
  private void chaos() { faults.maybeSlow(); faults.maybeCrashConsumer(); }

  @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "fleetflow-dispatch")
  public void onOrderCreated(ConsumerRecord<String, String> rec) {
    var ev = parse(rec.value()); MDC.put(CorrelationFilter.KEY, ev.correlationId());
    try {
      chaos();
      boolean first = idem.runOnce("order-created-handler", ev.eventId(), () -> tx.requestDispatch(ev.orgId(), ev.aggregateId()));
      if (first) metrics.eventProcessed("order-created"); else log.info("Duplicate event {} ignored", ev.eventId());
    } catch (RuntimeException e) { metrics.eventFailed("order-created"); throw e; } finally { MDC.remove(CorrelationFilter.KEY); }
  }

  @KafkaListener(topics = Topics.DISPATCH_REQUESTED, groupId = "fleetflow-dispatch")
  public void onDispatchRequested(ConsumerRecord<String, String> rec) {
    var ev = parse(rec.value()); MDC.put(CorrelationFilter.KEY, ev.correlationId());
    try {
      chaos(); faults.maybeFail(FaultInjector.Fault.DISPATCH_FAILURE);
      if (idem.seen("dispatch-handler", ev.eventId())) { log.info("Duplicate event {} ignored", ev.eventId()); return; }
      var result = dispatch.dispatch(ev.orgId(), ev.aggregateId());
      log.info("Dispatch for order {} -> {}", ev.aggregateId(), result);
      idem.mark("dispatch-handler", ev.eventId()); // only after success; an exception above leaves it unmarked so the retry re-runs
      metrics.eventProcessed("dispatch");
    } catch (RuntimeException e) { metrics.eventFailed("dispatch"); throw e; } finally { MDC.remove(CorrelationFilter.KEY); }
  }
}

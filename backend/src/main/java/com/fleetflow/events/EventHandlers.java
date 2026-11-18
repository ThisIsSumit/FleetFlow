package com.fleetflow.events;
import com.fasterxml.jackson.databind.*;
import com.fleetflow.common.CorrelationFilter;
import com.fleetflow.dispatch.*;
import com.fleetflow.notify.*;
import com.fleetflow.ops.*;
import com.fleetflow.outbox.Topics;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;
/** The effect of every consumed event, independent of transport. Kafka listeners delegate here; with the log publisher
 *  (no Kafka) {@link com.fleetflow.outbox.LoggingEventPublisher} calls {@link #route} in-process, so the dispatch and
 *  notification pipelines work in local development too. Every handler is idempotent (processed_events ledger). */
@Service
public class EventHandlers {
  private static final Logger log = LoggerFactory.getLogger(EventHandlers.class);
  record Envelope(UUID eventId, String eventType, UUID orgId, UUID aggregateId, String correlationId, JsonNode payload) {}
  private final ObjectMapper om; private final IdempotencyService idem; private final DispatchTx tx; private final DispatchService dispatch;
  private final FaultInjector faults; private final FleetMetrics metrics; private final NotificationService notes; private final NotificationRequester requester; private final JdbcTemplate jdbc;
  public EventHandlers(ObjectMapper om, IdempotencyService idem, DispatchTx tx, DispatchService dispatch, FaultInjector faults, FleetMetrics metrics,
      NotificationService notes, NotificationRequester requester, JdbcTemplate jdbc) {
    this.om = om; this.idem = idem; this.tx = tx; this.dispatch = dispatch; this.faults = faults; this.metrics = metrics; this.notes = notes; this.requester = requester; this.jdbc = jdbc;
  }

  /** Malformed envelopes throw IllegalArgumentException, which the Kafka error handler treats as not retryable (straight to DLQ). */
  Envelope parse(String json) {
    try {
      JsonNode n = om.readTree(json);
      return new Envelope(UUID.fromString(n.get("eventId").asText()), n.path("eventType").asText(""), UUID.fromString(n.get("organizationId").asText()),
          UUID.fromString(n.get("aggregateId").asText()), n.path("correlationId").asText(""), n.path("payload"));
    } catch (Exception e) { throw new IllegalArgumentException("Malformed event envelope", e); }
  }
  private void chaos() { faults.maybeSlow(); faults.maybeCrashConsumer(); }

  /** In-process routing used when Kafka is not configured. Topics nobody consumes (e.g. driver.location.updated) are ignored. */
  public void route(String topic, String json) {
    switch (topic) {
      case Topics.ORDER_CREATED -> orderCreated(json);
      case Topics.DISPATCH_REQUESTED -> dispatchRequested(json);
      case Topics.DRIVER_UNAVAILABLE -> driverUnavailable(json);
      case Topics.DRIVER_ASSIGNED, Topics.DELIVERY_COMPLETED, Topics.DELIVERY_CANCELLED -> notificationTrigger(json);
      case Topics.NOTIFICATION -> notificationRequested(json);
      default -> { }
    }
  }

  public void orderCreated(String json) {
    var ev = parse(json); MDC.put(CorrelationFilter.KEY, ev.correlationId());
    try {
      chaos();
      boolean first = idem.runOnce("order-created-handler", ev.eventId(), () -> tx.requestDispatch(ev.orgId(), ev.aggregateId()));
      if (first) metrics.eventProcessed("order-created"); else log.info("Duplicate event {} ignored", ev.eventId());
    } catch (RuntimeException e) { metrics.eventFailed("order-created"); throw e; } finally { MDC.remove(CorrelationFilter.KEY); }
  }

  public void dispatchRequested(String json) {
    var ev = parse(json); MDC.put(CorrelationFilter.KEY, ev.correlationId());
    try {
      chaos(); faults.maybeFail(FaultInjector.Fault.DISPATCH_FAILURE);
      if (idem.seen("dispatch-handler", ev.eventId())) { log.info("Duplicate event {} ignored", ev.eventId()); return; }
      var result = dispatch.dispatch(ev.orgId(), ev.aggregateId());
      log.info("Dispatch for order {} -> {}", ev.aggregateId(), result);
      idem.mark("dispatch-handler", ev.eventId()); // only after success; an exception above leaves it unmarked so the retry re-runs
      metrics.eventProcessed("dispatch");
    } catch (RuntimeException e) { metrics.eventFailed("dispatch"); throw e; } finally { MDC.remove(CorrelationFilter.KEY); }
  }

  public void driverUnavailable(String json) {
    var ev = parse(json); MDC.put(CorrelationFilter.KEY, ev.correlationId());
    try {
      idem.runOnce("driver-unavailable-handler", ev.eventId(), () -> {
        int[] r = tx.reassignFromDriver(ev.orgId(), ev.aggregateId());
        log.info("Driver {} unavailable: {} order(s) re-queued, {} in progress", ev.aggregateId(), r[0], r[1]);
      });
    } finally { MDC.remove(CorrelationFilter.KEY); }
  }

  /** Stage 1: domain events -> notification.requested (via outbox). */
  public void notificationTrigger(String json) {
    var ev = parse(json); UUID orderId = ev.aggregateId(), org = ev.orgId();
    idem.runOnce("notification-trigger", ev.eventId(), () -> {
      var r = jdbc.queryForList("SELECT customer_name FROM orders WHERE id = ? AND organization_id = ?", orderId, org);
      if (r.isEmpty()) return;
      String customer = String.valueOf(r.get(0).get("customer_name"));
      switch (ev.eventType()) {
        case "DRIVER_ASSIGNED" -> {
          // The driver named is the one in the event, not whoever holds the order by the time this runs (it may have been reassigned).
          String driver = "A driver";
          if (ev.payload().hasNonNull("driverId")) {
            var d = jdbc.queryForList("SELECT name FROM drivers WHERE id = ? AND organization_id = ?", UUID.fromString(ev.payload().get("driverId").asText()), org);
            if (!d.isEmpty()) driver = String.valueOf(d.get(0).get("name"));
          }
          requester.request(org, "DRIVER_ASSIGNED", "Driver assigned", driver + " will deliver the order for " + customer + ".", orderId);
        }
        case "DELIVERY_COMPLETED" -> requester.request(org, "DELIVERY_COMPLETED", "Delivery completed", "Order for " + customer + " was delivered.", orderId);
        case "DELIVERY_CANCELLED" -> requester.request(org, "ORDER_CANCELLED", "Order cancelled", "Order for " + customer + " was cancelled.", orderId);
        default -> { /* DELIVERY_FAILED / DRIVER_ARRIVED share these topics but raise their own alerts elsewhere */ }
      }
    });
  }

  /** Stage 2: notification.requested -> notifications table. */
  public void notificationRequested(String json) {
    var ev = parse(json); JsonNode p = ev.payload();
    UUID orderId = p.hasNonNull("orderId") ? UUID.fromString(p.get("orderId").asText()) : null;
    idem.runOnce("notification-writer", ev.eventId(), () -> notes.create(ev.orgId(), p.get("type").asText(), p.get("title").asText(), p.get("message").asText(), orderId));
  }
}

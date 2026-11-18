package com.fleetflow.outbox;
import com.fleetflow.events.EventHandlers;
import org.slf4j.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
/** Publisher used without Kafka (fleetflow.events.publisher=log, the default outside Docker). It logs each event and, unless
 *  fleetflow.events.local-delivery=false, hands it to the in-process {@link EventHandlers} on one background thread, so orders
 *  still get dispatched and notifications still get written. There is no retry/DLQ here: a failed handler is logged, and the
 *  dispatch sweeper re-emits orders that are still waiting. Single instance only: other instances never see these events. */
@Component @ConditionalOnProperty(name = "fleetflow.events.publisher", havingValue = "log", matchIfMissing = true)
public class LoggingEventPublisher implements EventPublisher, DisposableBean {
  private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);
  private final ObjectProvider<EventHandlers> handlers; private final boolean localDelivery;
  private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(10_000),
      r -> { var t = new Thread(r, "local-events"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.CallerRunsPolicy());
  public LoggingEventPublisher(ObjectProvider<EventHandlers> handlers, @Value("${fleetflow.events.local-delivery:true}") boolean localDelivery) {
    this.handlers = handlers; this.localDelivery = localDelivery;
  }
  public void publish(String topic, String key, String json) {
    log.info("EVENT topic={} key={} {}", topic, key, json);
    if (!localDelivery || Topics.DRIVER_LOCATION.equals(topic)) return; // location telemetry has no consumer
    worker.execute(() -> {
      try { handlers.getObject().route(topic, json); }
      catch (Exception e) { log.warn("Local handling of {} failed: {}", topic, e.toString()); }
    });
  }
  @Override public void destroy() { worker.shutdownNow(); }
}

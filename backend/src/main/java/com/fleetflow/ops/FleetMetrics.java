package com.fleetflow.ops;
import io.micrometer.core.instrument.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Prometheus names: orders_created_total, dispatch_latency_seconds_*, driver_assignment_latency_seconds_*, events_processed_total, ... */
@Component
public class FleetMetrics {
  public final Counter ordersCreated, ordersCompleted, ordersFailed, dispatchAttempts, dispatchSuccess, dlq;
  public final Timer dispatchLatency, assignmentLatency;
  private final MeterRegistry reg;
  public FleetMetrics(MeterRegistry r, JdbcTemplate jdbc) {
    reg = r;
    ordersCreated = r.counter("orders.created"); ordersCompleted = r.counter("orders.completed"); ordersFailed = r.counter("orders.failed");
    dispatchAttempts = r.counter("dispatch.attempts"); dispatchSuccess = r.counter("dispatch.success"); dlq = r.counter("dlq.messages");
    dispatchLatency = Timer.builder("dispatch.latency").publishPercentileHistogram().register(r);
    assignmentLatency = Timer.builder("driver.assignment.latency").publishPercentileHistogram().register(r);
    Gauge.builder("orders.unassigned", jdbc, j -> { try { Long n = j.queryForObject("SELECT count(*) FROM orders WHERE status = 'DISPATCH_REQUESTED'", Long.class); return n == null ? 0 : n; } catch (Exception e) { return Double.NaN; } }).register(r);
  }
  public void eventProcessed(String consumer) { reg.counter("events.processed", "consumer", consumer).increment(); }
  public void eventFailed(String consumer) { reg.counter("events.failed", "consumer", consumer).increment(); }
}

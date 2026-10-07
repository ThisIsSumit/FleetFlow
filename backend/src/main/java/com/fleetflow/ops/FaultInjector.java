package com.fleetflow.ops;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Inert unless a fault is enabled through the dev-tools API (itself disabled unless fleetflow.dev-tools.enabled=true). */
@Component
public class FaultInjector {
  public enum Fault { KAFKA_UNAVAILABLE, REDIS_UNAVAILABLE, DB_TIMEOUT, CONSUMER_CRASH, SLOW_CONSUMER, DISPATCH_FAILURE }
  public static class SimulatedFaultException extends RuntimeException { public SimulatedFaultException(Fault f) { super("Simulated fault: " + f); } }
  private final Map<Fault, Long> active = new ConcurrentHashMap<>(); // value = parameter (delay ms, or crashes remaining)
  public void enable(Fault f, long param) { active.put(f, param); }
  public void disable(Fault f) { active.remove(f); }
  public Map<Fault, Long> active() { return Map.copyOf(active); }
  public void maybeFail(Fault f) { if (active.containsKey(f)) throw new SimulatedFaultException(f); }
  /** Fails the next N deliveries, then heals itself, which lets you watch retry and recovery. */
  public void maybeCrashConsumer() {
    Long left = active.computeIfPresent(Fault.CONSUMER_CRASH, (k, v) -> v - 1);
    if (left != null) { if (left <= 0) active.remove(Fault.CONSUMER_CRASH); throw new SimulatedFaultException(Fault.CONSUMER_CRASH); }
  }
  public void maybeSlow() {
    Long ms = active.get(Fault.SLOW_CONSUMER);
    if (ms != null) try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
  }
}

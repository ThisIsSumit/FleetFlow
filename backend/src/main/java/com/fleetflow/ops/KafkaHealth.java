package com.fleetflow.ops;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Broker reachability (cached 10 s) and consumer liveness. Reports DISABLED when running with the log publisher. */
@Component
public class KafkaHealth {
  private final boolean enabled; private final KafkaAdmin admin; private final ObjectProvider<KafkaListenerEndpointRegistry> registry;
  private volatile long at; private volatile String cached = "DOWN";
  public KafkaHealth(@Value("${fleetflow.events.publisher}") String publisher, KafkaAdmin admin, ObjectProvider<KafkaListenerEndpointRegistry> registry) { this.enabled = "kafka".equals(publisher); this.admin = admin; this.registry = registry; }
  public synchronized String broker() {
    if (!enabled) return "DISABLED";
    if (System.currentTimeMillis() - at < 10_000) return cached;
    Map<String, Object> props = new HashMap<>(admin.getConfigurationProperties()); props.put("request.timeout.ms", "2000"); props.put("default.api.timeout.ms", "2000");
    try (AdminClient c = AdminClient.create(props)) { c.describeCluster().nodes().get(2, TimeUnit.SECONDS); cached = "UP"; } catch (Exception e) { cached = "DOWN"; }
    at = System.currentTimeMillis(); return cached;
  }
  public String consumers() {
    if (!enabled) return "DISABLED";
    var r = registry.getIfAvailable(); if (r == null || r.getListenerContainers().isEmpty()) return "DOWN";
    return r.getListenerContainers().stream().allMatch(MessageListenerContainer::isRunning) ? "UP" : "DEGRADED";
  }
}

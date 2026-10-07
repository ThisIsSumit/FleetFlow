package com.fleetflow.outbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.common.CorrelationFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
@Service
public class OutboxService {
  private final OutboxRepository repo; private final ObjectMapper om;
  public OutboxService(OutboxRepository repo, ObjectMapper om) { this.repo = repo; this.om = om; }
  /** MANDATORY: throws if called outside a business transaction, so an event can never be written without its state change. */
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID enqueue(UUID orgId, UUID aggregateId, String eventType, String topic, String partitionKey, Map<String, Object> payload) {
    var e = new OutboxEvent();
    e.organizationId = orgId; e.aggregateId = aggregateId; e.eventType = eventType; e.topic = topic; e.partitionKey = partitionKey;
    String corr = Optional.ofNullable(MDC.get(CorrelationFilter.KEY)).orElse(UUID.randomUUID().toString());
    var env = new LinkedHashMap<String, Object>();
    env.put("eventId", e.id); env.put("eventType", eventType); env.put("organizationId", orgId); env.put("aggregateId", aggregateId);
    env.put("correlationId", corr); env.put("timestamp", Instant.now().toString()); env.put("payload", payload);
    try { e.payload = om.writeValueAsString(env); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    repo.save(e);
    return e.id;
  }
}

package com.fleetflow.outbox;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "outbox_events")
public class OutboxEvent {
  @Id public UUID id = UUID.randomUUID();
  @Column(name = "organization_id") public UUID organizationId;
  @Column(name = "aggregate_id") public UUID aggregateId;
  @Column(name = "event_type") public String eventType;
  public String topic;
  @Column(name = "partition_key") public String partitionKey;
  public String payload;
  public String status = "PENDING";
  public int attempts;
  @Column(name = "next_attempt_at") public Instant nextAttemptAt = Instant.now();
  @Column(name = "last_error") public String lastError;
  @Column(name = "created_at") public Instant createdAt = Instant.now();
  @Column(name = "sent_at") public Instant sentAt;
}

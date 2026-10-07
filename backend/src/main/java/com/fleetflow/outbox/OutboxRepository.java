package com.fleetflow.outbox;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  /** SKIP LOCKED lets several relay instances poll concurrently without double-publishing the same row. */
  @Query(value = "SELECT * FROM outbox_events WHERE status = 'PENDING' AND next_attempt_at <= now() ORDER BY created_at LIMIT :n FOR UPDATE SKIP LOCKED", nativeQuery = true)
  List<OutboxEvent> lockBatch(int n);
}

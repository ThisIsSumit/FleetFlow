package com.fleetflow.notify;
import com.fleetflow.outbox.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Producers ask for a notification by emitting notification.requested through the outbox; NotificationConsumers writes the row.
 *  Joins the caller's transaction when there is one, so a state change and its notification request commit together. */
@Service
public class NotificationRequester {
  private final OutboxService outbox; public NotificationRequester(OutboxService outbox) { this.outbox = outbox; }
  @Transactional
  public void request(UUID org, String type, String title, String message, UUID orderId) {
    var p = new LinkedHashMap<String, Object>(); p.put("type", type); p.put("title", title); p.put("message", message); p.put("orderId", orderId);
    outbox.enqueue(org, orderId != null ? orderId : org, "NOTIFICATION_REQUESTED", Topics.NOTIFICATION, org.toString(), p);
  }
}

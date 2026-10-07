package com.fleetflow.notify;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** Raises DELIVERY_DELAYED once per order when its scheduled time has passed and it is still open. System job, so it is cross-tenant by design. */
@Component
public class DelayedDeliveryScanner {
  private final JdbcTemplate jdbc; private final NotificationRequester notifications;
  public DelayedDeliveryScanner(JdbcTemplate jdbc, NotificationRequester notifications) { this.jdbc = jdbc; this.notifications = notifications; }
  @Scheduled(fixedDelay = 60000)
  public void scan() {
    var rows = jdbc.queryForList("SELECT id, organization_id, customer_name FROM orders o WHERE scheduled_at < now() AND status IN ('DISPATCH_REQUESTED','ASSIGNED','ARRIVED','IN_TRANSIT') AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.order_id = o.id AND n.type = 'DELIVERY_DELAYED') LIMIT 100");
    for (var r : rows) notifications.request((UUID) r.get("organization_id"), "DELIVERY_DELAYED", "Delivery delayed", "Order for " + r.get("customer_name") + " is past its scheduled time.", (UUID) r.get("id"));
  }
}

package com.fleetflow.notify;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
@Service
public class NotificationService {
  public record Item(UUID id, String type, String title, String message, UUID orderId, Instant createdAt, boolean read) {}
  private final JdbcTemplate jdbc; public NotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public void create(UUID org, String type, String title, String message, UUID orderId) {
    jdbc.update("INSERT INTO notifications(id, organization_id, type, title, message, order_id) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING", UUID.randomUUID(), org, type, title, message.length() > 300 ? message.substring(0, 300) : message, orderId);
  }
  public List<Item> list(UUID user, UUID org, int limit) {
    return jdbc.query("SELECT n.*, (r.user_id IS NOT NULL) AS is_read FROM notifications n LEFT JOIN notification_reads r ON r.notification_id = n.id AND r.user_id = ? WHERE n.organization_id = ? ORDER BY n.created_at DESC LIMIT ?",
        (rs, i) -> new Item(rs.getObject("id", UUID.class), rs.getString("type"), rs.getString("title"), rs.getString("message"), rs.getObject("order_id", UUID.class), rs.getTimestamp("created_at").toInstant(), rs.getBoolean("is_read")), user, org, Math.min(limit, 100));
  }
  public long unread(UUID user, UUID org) {
    Long n = jdbc.queryForObject("SELECT count(*) FROM notifications n WHERE n.organization_id = ? AND n.created_at > now() - interval '30 days' AND NOT EXISTS (SELECT 1 FROM notification_reads r WHERE r.notification_id = n.id AND r.user_id = ?)", Long.class, org, user);
    return n == null ? 0 : n;
  }
  public void markRead(UUID user, UUID org, UUID id) { jdbc.update("INSERT INTO notification_reads(user_id, notification_id) SELECT ?, id FROM notifications WHERE id = ? AND organization_id = ? ON CONFLICT DO NOTHING", user, id, org); }
  public void markAllRead(UUID user, UUID org) { jdbc.update("INSERT INTO notification_reads(user_id, notification_id) SELECT ?, id FROM notifications WHERE organization_id = ? AND created_at > now() - interval '30 days' ON CONFLICT DO NOTHING", user, org); }
}

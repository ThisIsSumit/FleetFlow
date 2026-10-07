package com.fleetflow.notify;
import com.fleetflow.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/notifications") @PreAuthorize("hasAnyRole('OWNER','ADMIN','DISPATCHER')")
public class NotificationController {
  public record View(long unread, List<NotificationService.Item> items) {}
  private final NotificationService svc; public NotificationController(NotificationService svc) { this.svc = svc; }
  @GetMapping public View list(@RequestParam(defaultValue = "30") int limit) { var p = TenantContext.current(); return new View(svc.unread(p.userId(), p.organizationId()), svc.list(p.userId(), p.organizationId(), limit)); }
  @PostMapping("/{id}/read") public ResponseEntity<Void> read(@PathVariable UUID id) { var p = TenantContext.current(); svc.markRead(p.userId(), p.organizationId(), id); return ResponseEntity.noContent().build(); }
  @PostMapping("/read-all") public ResponseEntity<Void> readAll() { var p = TenantContext.current(); svc.markAllRead(p.userId(), p.organizationId()); return ResponseEntity.noContent().build(); }
}

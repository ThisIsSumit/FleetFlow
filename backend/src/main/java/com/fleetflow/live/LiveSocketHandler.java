package com.fleetflow.live;
import com.fasterxml.jackson.databind.*;
import com.fleetflow.common.*;
import com.fleetflow.domain.Role;
import com.fleetflow.fleet.FleetRepos.DriverRepository;
import com.fleetflow.security.AuthPrincipal;
import com.fleetflow.security.SessionRevocation;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Staff sockets receive the org's live location stream. Any authenticated socket may send {"type":"location",...}; DRIVER role sockets are pinned to their own driver record.
 *  A socket lives no longer than the access token it was opened with, and is closed as soon as its session is revoked; the client reconnects with a fresh token. */
@Component
public class LiveSocketHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(LiveSocketHandler.class);
  /** 4001 = "token expired or revoked, reconnect with a new token" (application-defined close code). */
  static final CloseStatus AUTH_EXPIRED = new CloseStatus(4001, "Token expired or revoked");
  private final LiveBroadcaster live; private final LocationService locations; private final DriverRepository drivers; private final RateLimiter limiter; private final ObjectMapper om; private final SessionRevocation revocation;
  private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();
  @org.springframework.beans.factory.annotation.Value("${fleetflow.rate-limit.location-per-minute}") private int perMinute;
  public LiveSocketHandler(LiveBroadcaster l, LocationService loc, DriverRepository d, RateLimiter r, ObjectMapper om, SessionRevocation revocation) { live = l; locations = loc; drivers = d; limiter = r; this.om = om; this.revocation = revocation; }
  private static AuthPrincipal principal(WebSocketSession s) { return (AuthPrincipal) s.getAttributes().get("principal"); }
  private boolean stillAuthorized(WebSocketSession s) {
    var exp = (Instant) s.getAttributes().get("expiresAt");
    return (exp == null || Instant.now().isBefore(exp)) && !revocation.isRevoked(principal(s).sessionId());
  }
  private void closeQuietly(WebSocketSession s, CloseStatus st) { try { s.close(st); } catch (Exception ignored) {} }

  @Override public void afterConnectionEstablished(WebSocketSession s) {
    var p = principal(s); open.put(s.getId(), s);
    if (p.role() != Role.DRIVER) live.register(p.organizationId(), s);
  }
  @Override public void afterConnectionClosed(WebSocketSession s, CloseStatus st) { open.remove(s.getId()); live.unregister(principal(s).organizationId(), s); }

  /** Closes sockets whose token has expired or whose session was revoked (logout, disable, role change...). */
  @Scheduled(fixedDelay = 30000)
  public void closeExpired() {
    for (var s : open.values()) if (!stillAuthorized(s)) { log.debug("Closing socket {}: token expired or revoked", s.getId()); closeQuietly(s, AUTH_EXPIRED); }
  }

  private void reply(WebSocketSession s, String code) { try { s.sendMessage(new TextMessage("{\"type\":\"error\",\"code\":\"" + code + "\"}")); } catch (Exception ignored) {} }
  @Override protected void handleTextMessage(WebSocketSession s, TextMessage msg) {
    var p = principal(s);
    if (!stillAuthorized(s)) { closeQuietly(s, AUTH_EXPIRED); return; }
    // Socket threads have no security context; set one so row-level security scopes these queries to the caller's organization.
    var ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name()))));
    SecurityContextHolder.setContext(ctx);
    try {
      JsonNode n = om.readTree(msg.getPayload());
      if (!"location".equals(n.path("type").asText())) return;
      UUID driverId = p.role() == Role.DRIVER
          ? drivers.findByUserIdAndOrganizationId(p.userId(), p.organizationId()).map(d -> d.id).orElse(null)   // never trust a driverId sent by a driver
          : UUID.fromString(n.path("driverId").asText());
      if (driverId == null) { reply(s, "NO_DRIVER_PROFILE"); return; }
      if (!limiter.hit("rl:loc:" + driverId, perMinute, Duration.ofMinutes(1)).allowed()) { reply(s, "RATE_LIMITED"); return; }
      locations.update(p.organizationId(), driverId, n.get("latitude").asDouble(), n.get("longitude").asDouble());
    } catch (ApiException e) { reply(s, e.code); } catch (Exception e) { reply(s, "BAD_MESSAGE"); }
    finally { SecurityContextHolder.clearContext(); }
  }
}

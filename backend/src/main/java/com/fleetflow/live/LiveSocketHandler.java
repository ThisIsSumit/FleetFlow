package com.fleetflow.live;
import com.fasterxml.jackson.databind.*;
import com.fleetflow.common.*;
import com.fleetflow.domain.Role;
import com.fleetflow.fleet.FleetRepos.DriverRepository;
import com.fleetflow.security.AuthPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.time.Duration;
import java.util.UUID;
/** Staff sockets receive the org's live location stream. Any authenticated socket may send {"type":"location",...}; DRIVER role sockets are pinned to their own driver record. */
@Component
public class LiveSocketHandler extends TextWebSocketHandler {
  private final LiveBroadcaster live; private final LocationService locations; private final DriverRepository drivers; private final RateLimiter limiter; private final ObjectMapper om;
  @org.springframework.beans.factory.annotation.Value("${fleetflow.rate-limit.location-per-minute}") private int perMinute;
  public LiveSocketHandler(LiveBroadcaster l, LocationService loc, DriverRepository d, RateLimiter r, ObjectMapper om) { live = l; locations = loc; drivers = d; limiter = r; this.om = om; }
  private static AuthPrincipal principal(WebSocketSession s) { return (AuthPrincipal) s.getAttributes().get("principal"); }
  @Override public void afterConnectionEstablished(WebSocketSession s) { var p = principal(s); if (p.role() != Role.DRIVER) live.register(p.organizationId(), s); }
  @Override public void afterConnectionClosed(WebSocketSession s, CloseStatus st) { live.unregister(principal(s).organizationId(), s); }
  private void reply(WebSocketSession s, String code) { try { s.sendMessage(new TextMessage("{\"type\":\"error\",\"code\":\"" + code + "\"}")); } catch (Exception ignored) {} }
  @Override protected void handleTextMessage(WebSocketSession s, TextMessage msg) {
    var p = principal(s);
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
  }
}

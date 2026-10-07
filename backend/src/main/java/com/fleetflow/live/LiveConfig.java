package com.fleetflow.live;
import com.fleetflow.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.*;
@Configuration @EnableWebSocket
public class LiveConfig implements WebSocketConfigurer {
  private final LiveSocketHandler handler; private final JwtService jwt; private final List<String> origins;
  public LiveConfig(LiveSocketHandler handler, JwtService jwt, @Value("${fleetflow.cors-origins}") List<String> origins) { this.handler = handler; this.jwt = jwt; this.origins = origins; }
  @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry reg) {
    reg.addHandler(handler, "/ws/live").addInterceptors(new HandshakeInterceptor() {
      /** Browsers cannot set headers on WebSockets, so the short-lived access token comes as ?token=. Keep access logs from recording query strings. */
      public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler h, Map<String, Object> attrs) {
        try { attrs.put("principal", jwt.parse(UriComponentsBuilder.fromUri(req.getURI()).build().getQueryParams().getFirst("token"))); return true; }
        catch (Exception e) { res.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
      }
      public void afterHandshake(ServerHttpRequest a, ServerHttpResponse b, WebSocketHandler c, Exception d) {}
    }).setAllowedOrigins(origins.toArray(String[]::new));
  }
  @Bean RedisMessageListenerContainer liveContainer(RedisConnectionFactory cf, LiveBroadcaster b) {
    var c = new RedisMessageListenerContainer(); c.setConnectionFactory(cf); c.addMessageListener(b, new PatternTopic("live:*")); return c;
  }
}

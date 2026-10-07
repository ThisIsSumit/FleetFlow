package com.fleetflow.live;
import org.slf4j.*;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** WebSocket registry + Redis pub/sub bridge: any instance can publish, every instance forwards to its own sockets (horizontal scaling).
 *  If Redis is down, publishing falls back to local delivery, so a single instance keeps working. */
@Component
public class LiveBroadcaster implements MessageListener {
  private static final Logger log = LoggerFactory.getLogger(LiveBroadcaster.class);
  private final Map<UUID, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();
  private final StringRedisTemplate redis;
  public LiveBroadcaster(StringRedisTemplate redis) { this.redis = redis; }
  public void register(UUID org, WebSocketSession s) { sessions.computeIfAbsent(org, k -> new ConcurrentHashMap<>()).put(s.getId(), new ConcurrentWebSocketSessionDecorator(s, 5000, 64 * 1024)); }
  public void unregister(UUID org, WebSocketSession s) { var m = sessions.get(org); if (m != null) m.remove(s.getId()); }
  public void publish(UUID org, String json) {
    try { redis.convertAndSend("live:" + org, json); } catch (Exception e) { deliverLocal(org, json); }
  }
  @Override public void onMessage(Message message, byte[] pattern) {
    try { deliverLocal(UUID.fromString(new String(message.getChannel(), StandardCharsets.UTF_8).substring(5)), new String(message.getBody(), StandardCharsets.UTF_8)); }
    catch (Exception e) { log.warn("Bad live message: {}", e.toString()); }
  }
  private void deliverLocal(UUID org, String json) {
    var m = sessions.get(org); if (m == null) return;
    for (var s : m.values()) try { if (s.isOpen()) s.sendMessage(new TextMessage(json)); } catch (Exception e) { log.debug("Dropping slow/closed socket {}", s.getId()); }
  }
}

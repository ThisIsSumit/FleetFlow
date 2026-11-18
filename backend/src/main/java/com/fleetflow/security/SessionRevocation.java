package com.fleetflow.security;
import com.fleetflow.domain.RefreshSession;
import com.fleetflow.domain.Repos.SessionRepository;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;
/** Ends sessions immediately, not just at the next refresh. Deleting the refresh session stops new access tokens; the session id
 *  also goes on a Redis denylist for one access-token lifetime, which JwtAuthFilter and the WebSocket handler check, so tokens
 *  already issued for that session stop working too (logout, revoke, role change, disable, removal, password reset, theft).
 *  If Redis is unavailable the check fails open for a few seconds at a time (tokens then live out their 15 minutes, as before). */
@Service
public class SessionRevocation {
  private static final Logger log = LoggerFactory.getLogger(SessionRevocation.class);
  private final SessionRepository sessions; private final StringRedisTemplate redis; private final Duration ttl;
  private volatile long redisDownUntil;
  public SessionRevocation(SessionRepository sessions, StringRedisTemplate redis, @Value("${fleetflow.jwt.access-ttl-seconds}") long accessTtl) {
    this.sessions = sessions; this.redis = redis; this.ttl = Duration.ofSeconds(accessTtl + 60);
  }
  static String key(UUID sid) { return "revoked:sid:" + sid; }

  /** Every session of the user. Call inside the business transaction. */
  public void revokeUser(UUID userId) {
    var ids = sessions.findByUserIdOrderByLastUsedAtDesc(userId).stream().map(RefreshSession::getId).toList();
    sessions.deleteByUserId(userId);
    ids.forEach(this::denylist);
  }
  public void revokeSession(RefreshSession s) { sessions.delete(s); denylist(s.getId()); }

  private void denylist(UUID sid) {
    try { redis.opsForValue().set(key(sid), "1", ttl); }
    catch (Exception e) { log.warn("Could not denylist session {} (its access tokens stay valid until they expire): {}", sid, e.toString()); }
  }
  public boolean isRevoked(UUID sid) {
    if (System.currentTimeMillis() < redisDownUntil) return false;
    try { return Boolean.TRUE.equals(redis.hasKey(key(sid))); }
    catch (Exception e) { redisDownUntil = System.currentTimeMillis() + 5000; log.warn("Session denylist unavailable, failing open: {}", e.toString()); return false; }
  }
}

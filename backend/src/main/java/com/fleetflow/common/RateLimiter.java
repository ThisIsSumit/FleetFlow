package com.fleetflow.common;
import org.slf4j.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
/** Fixed-window counter in Redis (atomic INCR + PEXPIRE in one Lua call). Fails OPEN if Redis is unavailable.
 *  Trade-off: a fixed window allows up to 2x the limit across a window boundary; a sliding window costs more Redis work. */
@Component
public class RateLimiter {
  private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
  public record Decision(boolean allowed, long retryAfterSeconds) {}
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(
      "local n = redis.call('INCR', KEYS[1]) if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end return {n, redis.call('PTTL', KEYS[1])}", List.class);
  private final StringRedisTemplate redis;
  public RateLimiter(StringRedisTemplate redis) { this.redis = redis; }
  @SuppressWarnings("unchecked")
  public Decision hit(String key, int limit, Duration window) {
    try {
      List<Long> r = redis.execute(SCRIPT, List.of(key), String.valueOf(window.toMillis()));
      if (r == null || r.size() < 2) return new Decision(true, 0);
      if (r.get(0) > limit) return new Decision(false, Math.max(1, (r.get(1) + 999) / 1000));
      return new Decision(true, 0);
    } catch (Exception e) { log.warn("Rate limiter unavailable, failing open: {}", e.toString()); return new Decision(true, 0); }
  }
}

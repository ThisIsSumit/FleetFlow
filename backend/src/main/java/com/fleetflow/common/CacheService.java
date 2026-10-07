package com.fleetflow.common;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.function.Supplier;
/** Read-through Redis cache with hit/miss metrics (redis_cache_hits_total / redis_cache_misses_total, tagged by cache).
 *  Fails open: if Redis is down the loader runs every time. Only for data where a short staleness is acceptable. */
@Component
public class CacheService {
  private static final Logger log = LoggerFactory.getLogger(CacheService.class);
  private final StringRedisTemplate redis; private final ObjectMapper om; private final MeterRegistry reg;
  public CacheService(StringRedisTemplate redis, ObjectMapper om, MeterRegistry reg) { this.redis = redis; this.om = om; this.reg = reg; }
  private static String k(String cache, String key) { return "cache:" + cache + ":" + key; }
  public <T> T get(String cache, String key, Class<T> type, Duration ttl, Supplier<T> loader) {
    try { String v = redis.opsForValue().get(k(cache, key)); if (v != null) { T t = om.readValue(v, type); reg.counter("redis.cache.hits", "cache", cache).increment(); return t; } }
    catch (Exception e) { log.debug("cache read failed for {}: {}", cache, e.toString()); }
    reg.counter("redis.cache.misses", "cache", cache).increment();
    T val = loader.get();
    if (val != null) try { redis.opsForValue().set(k(cache, key), om.writeValueAsString(val), ttl); } catch (Exception e) { log.debug("cache write failed for {}: {}", cache, e.toString()); }
    return val;
  }
  public void evict(String cache, String key) { try { redis.delete(k(cache, key)); } catch (Exception e) { log.debug("cache evict failed: {}", e.toString()); } }
}

package com.fleetflow.dispatch;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
/** Redis GEO index, one key per organization (tenant isolation). GEO members cannot expire individually, so a companion sorted set
 *  geo:seen:{org} (score = last ping, ms) provides freshness: dispatch ignores drivers not seen recently and a janitor evicts stale ones. */
@Service
public class DriverGeoService {
  private static final Logger log = LoggerFactory.getLogger(DriverGeoService.class);
  public record Nearby(UUID driverId, double distanceKm) {}
  private final StringRedisTemplate redis;
  @Autowired private com.fleetflow.ops.FaultInjector faults;
  @Value("${fleetflow.geo.dispatch-fresh-seconds:120}") private long freshSeconds;
  @Value("${fleetflow.geo.evict-after-seconds:900}") private long evictSeconds;
  public DriverGeoService(StringRedisTemplate redis) { this.redis = redis; }
  static String key(UUID org) { return "geo:drivers:" + org; }
  static String seen(UUID org) { return "geo:seen:" + org; }
  private static final String ORGS = "geo:orgs";
  public void update(UUID org, UUID driverId, double lat, double lng) {
    faults.maybeFail(com.fleetflow.ops.FaultInjector.Fault.REDIS_UNAVAILABLE);
    redis.opsForGeo().add(key(org), new Point(lng, lat), driverId.toString());
    redis.opsForZSet().add(seen(org), driverId.toString(), System.currentTimeMillis());
    redis.opsForSet().add(ORGS, org.toString());
  }
  public void removeQuietly(UUID org, UUID driverId) {
    try { redis.opsForGeo().remove(key(org), driverId.toString()); redis.opsForZSet().remove(seen(org), driverId.toString()); }
    catch (Exception e) { log.warn("Redis GEO remove failed for {}: {}", driverId, e.toString()); }
  }
  /** Nearest first, only drivers whose last ping is within the freshness window. */
  public List<Nearby> nearby(UUID org, double lat, double lng, double radiusKm) {
    faults.maybeFail(com.fleetflow.ops.FaultInjector.Fault.REDIS_UNAVAILABLE);
    GeoResults<GeoLocation<String>> res = redis.opsForGeo().search(key(org), GeoReference.fromCoordinate(lng, lat),
        new Distance(radiusKm, Metrics.KILOMETERS), GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending().limit(50));
    if (res == null) return List.of();
    double cutoff = System.currentTimeMillis() - freshSeconds * 1000.0; var out = new ArrayList<Nearby>();
    for (var r : res.getContent()) {
      Double last = redis.opsForZSet().score(seen(org), r.getContent().getName());
      if (last != null && last >= cutoff) out.add(new Nearby(UUID.fromString(r.getContent().getName()), r.getDistance().getValue()));
    }
    return out;
  }
  public Map<UUID, Point> positions(UUID org, Collection<UUID> ids) {
    var out = new HashMap<UUID, Point>(); if (ids.isEmpty()) return out;
    try {
      var pts = redis.opsForGeo().position(key(org), ids.stream().map(UUID::toString).toArray(String[]::new)); int i = 0;
      for (UUID id : ids) { Point p = pts == null ? null : pts.get(i++); if (p != null) out.put(id, p); }
    } catch (Exception e) { log.warn("Redis GEO positions failed: {}", e.toString()); }
    return out;
  }
  /** Removes drivers not seen for evict-after-seconds from both structures. Safe to run on every instance. Returns how many were evicted. */
  @Scheduled(fixedDelayString = "${fleetflow.geo.janitor-ms:60000}")
  public int evictStale() {
    int n = 0;
    try {
      double cutoff = System.currentTimeMillis() - evictSeconds * 1000.0;
      for (String o : Optional.ofNullable(redis.opsForSet().members(ORGS)).orElse(Set.of())) {
        UUID org = UUID.fromString(o);
        var stale = redis.opsForZSet().rangeByScore(seen(org), 0, cutoff);
        if (stale == null || stale.isEmpty()) continue;
        redis.opsForGeo().remove(key(org), stale.toArray().toString()); redis.opsForZSet().remove(seen(org), stale.toArray()); n += stale.size();
      }
    } catch (Exception e) { log.debug("Geo janitor skipped: {}", e.toString()); }
    return n;
  }
}

# Redis

| Use | Key | Notes |
|---|---|---|
| Driver positions | `geo:drivers:{org}` (GEO) | rebuilt as drivers ping; no staleness expiry yet |
| Assignment lock | `lock:driver:{id}` | 10 s TTL, token-checked Lua release |
| Rate limits | `rl:login:{ip}`, `rl:orders:{user}`, `rl:loc:{driver}` | fixed window via one Lua call; fails open |
| Position freshness | `geo:seen:{org}` (ZSET, score = last ping ms), `geo:orgs` (SET) | dispatch ignores drivers not seen in 120 s; a janitor evicts after 900 s |
| Cache | `cache:{name}:{key}` | dispatch settings (60 s, evicted after commit), dashboard summary (10 s), geocoder results (24 h) |
| Live fan-out | pub/sub `live:{org}` | lets any instance push to every instance's sockets |

Limits: login 5/min/IP, order creation 100/min/user, location 60/min/driver (configurable). 429 responses use the standard error body plus `Retry-After`.
Cache hits and misses are counted per cache (`redis_cache_hits_total`, `redis_cache_misses_total`, tag `cache`); the cache fails open, so a Redis outage only costs database load. Idempotency uses PostgreSQL (`processed_events`), not Redis, so a Redis flush cannot cause re-processing.

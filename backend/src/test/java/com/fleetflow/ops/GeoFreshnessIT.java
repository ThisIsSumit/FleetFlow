package com.fleetflow.ops;
import com.fleetflow.dispatch.DriverGeoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Testcontainers
class GeoFreshnessIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
  @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername); r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    r.add("fleetflow.jwt.secret", () -> "test-secret-test-secret-test-secret-123456"); r.add("fleetflow.refresh.cookie-secure", () -> "false"); r.add("fleetflow.outbox.poll-ms", () -> "3600000");
    r.add("fleetflow.geo.janitor-ms", () -> "3600000");
  }
  @Autowired DriverGeoService geo; @Autowired StringRedisTemplate redisTemplate;
  @Test void staleDriversAreIgnoredForDispatchThenEvicted() {
    UUID org = UUID.randomUUID(), driver = UUID.randomUUID();
    geo.update(org, driver, 30.74, 76.78);
    assertEquals(List.of(driver), geo.nearby(org, 30.74, 76.78, 5).stream().map(DriverGeoService.Nearby::driverId).toList());
    redisTemplate.opsForZSet().add("geo:seen:" + org, driver.toString(), System.currentTimeMillis() - 200_000);       // last ping 200 s ago (> 120 s)
    assertTrue(geo.nearby(org, 30.74, 76.78, 5).isEmpty(), "stale driver must not be a dispatch candidate");
    assertFalse(geo.positions(org, List.of(driver)).isEmpty(), "still on the map until evicted");
    redisTemplate.opsForZSet().add("geo:seen:" + org, driver.toString(), System.currentTimeMillis() - 1_000_000);     // > 900 s
    assertTrue(geo.evictStale() >= 1);
    assertTrue(geo.positions(org, List.of(driver)).isEmpty());
  }
}
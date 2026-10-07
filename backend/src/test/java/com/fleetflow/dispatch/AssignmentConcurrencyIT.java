package com.fleetflow.dispatch;
import com.fleetflow.domain.Organization;
import com.fleetflow.domain.Repos.OrganizationRepository;
import com.fleetflow.fleet.Entities.*;
import com.fleetflow.fleet.Enums.*;
import com.fleetflow.fleet.FleetRepos.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Testcontainers
class AssignmentConcurrencyIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
  @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername); r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    r.add("fleetflow.jwt.secret", () -> "test-secret-test-secret-test-secret-123456"); r.add("fleetflow.refresh.cookie-secure", () -> "false");
    r.add("fleetflow.outbox.poll-ms", () -> "3600000");
  }
  @Autowired io.micrometer.core.instrument.MeterRegistry registry;
  @Autowired OrganizationRepository orgs; @Autowired DriverRepository drivers; @Autowired OrderRepository orders;
  @Autowired SettingsService settings; @Autowired AssignmentService assignment; @Autowired DispatchTx tx; @Autowired IdempotencyService idem; @Autowired JdbcTemplate jdbc;

  record Setup(UUID org, UUID driver, List<UUID> orderIds) {}
  Setup setup(int maxWorkload, int nOrders) {
    var org = orgs.save(new Organization("Org " + UUID.randomUUID()));
    var v = DispatchSettings.Values.defaults();
    settings.save(org.getId(), new DispatchSettings.Values(v.radiusKm(), maxWorkload, v.weights(), true));
    var d = new Driver(); d.organizationId = org.getId(); d.name = "X"; d.status = DriverStatus.AVAILABLE; drivers.save(d);
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < nOrders; i++) {
      var o = new DeliveryOrder(); o.organizationId = org.getId(); o.customerName = "C" + i; o.pickupAddress = "a"; o.dropAddress = "b"; o.items = "[]";
      o.priority = Priority.NORMAL; o.status = OrderStatus.DISPATCH_REQUESTED; orders.save(o); ids.add(o.id);
    }
    return new Setup(org.getId(), d.id, ids);
  }
  /** Releases all callers at the same instant and returns how many reported success. */
  int race(List<UUID> orderIds, Function<UUID, Boolean> attempt) throws Exception {
    var pool = Executors.newFixedThreadPool(orderIds.size()); var start = new CountDownLatch(1); var wins = new AtomicInteger();
    var futures = new ArrayList<Future<?>>();
    for (UUID id : orderIds) futures.add(pool.submit(() -> { start.await(); try { if (attempt.apply(id)) wins.incrementAndGet(); } catch (RuntimeException e) { /* lost the race */ } return null; }));
    start.countDown(); for (var f : futures) f.get(30, TimeUnit.SECONDS); pool.shutdown();
    return wins.get();
  }
  int assignedTo(UUID driver) { return jdbc.queryForObject("SELECT count(*) FROM orders WHERE driver_id = ? AND status = 'ASSIGNED'", Integer.class, driver); }

  @Test void tenOrdersOneDriver_exactlyOneAssignmentSucceeds() throws Exception {
    var s = setup(1, 10);
    assertEquals(1, race(s.orderIds(), id -> assignment.tryAssign(s.org(), id, s.driver())));
    assertEquals(1, assignedTo(s.driver()));
    assertEquals("BUSY", jdbc.queryForObject("SELECT status FROM drivers WHERE id = ?", String.class, s.driver()));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE organization_id = ? AND event_type = 'DRIVER_ASSIGNED'", Integer.class, s.org()));
  }
  @Test void workloadLimitIsRespectedUnderContention() throws Exception {
    var s = setup(3, 10);
    assertEquals(3, race(s.orderIds(), id -> assignment.tryAssign(s.org(), id, s.driver())));
    assertEquals(3, assignedTo(s.driver()));
  }
  @Test void databaseAloneIsCorrectWhenRedisLockIsBypassed() throws Exception {
    var s = setup(1, 10);
    assertEquals(1, race(s.orderIds(), id -> tx.assign(s.org(), id, s.driver())));
    assertEquals(1, assignedTo(s.driver()));
  }
  @Test void duplicateEventIsProcessedOnce() {
    var ev = UUID.randomUUID(); var runs = new AtomicInteger();
    assertTrue(idem.runOnce("t", ev, runs::incrementAndGet)); assertFalse(idem.runOnce("t", ev, runs::incrementAndGet));
    assertEquals(1, runs.get());
  }
  @Test void failedEffectRollsBackTheLedgerSoRetryReprocesses() {
    var ev = UUID.randomUUID();
    assertThrows(IllegalStateException.class, () -> idem.runOnce("t2", ev, () -> { throw new IllegalStateException("boom"); }));
    assertFalse(idem.seen("t2", ev));
    assertTrue(idem.runOnce("t2", ev, () -> {}));
  }
  @Test void settingsAreCachedInRedisAndEvictedOnSave() {
    var s = setup(1, 0); var c = registry.find("redis.cache.hits").tag("cache", "dispatch-settings").counter(); double before = c == null ? 0 : c.count();
    settings.get(s.org()); settings.get(s.org());
    assertTrue(registry.find("redis.cache.hits").tag("cache", "dispatch-settings").counter().count() >= before + 1, "second read must be a cache hit");
    var v = DispatchSettings.Values.defaults(); settings.save(s.org(), new DispatchSettings.Values(7, 2, v.weights(), true));
    assertEquals(7.0, settings.get(s.org()).radiusKm(), "save must invalidate the cached value");
  }
  @Test void sweeperFailsAnOrderAfterTheAttemptBudgetAndRaisesAnAlert() {
    var s = setup(1, 1); UUID order = s.orderIds().get(0);
    jdbc.update("UPDATE orders SET dispatch_attempts = 20 WHERE id = ?", order);
    tx.resweepOne(order, 20);
    assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'DELIVERY_FAILED'", Integer.class, order));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'NOTIFICATION_REQUESTED'", Integer.class, order));
  }
  @Test void sweeperReEmitsWhileBudgetRemains() {
    var s = setup(1, 1); UUID order = s.orderIds().get(0);
    tx.resweepOne(order, 20);
    assertEquals("DISPATCH_REQUESTED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order));
    assertEquals(1, jdbc.queryForObject("SELECT dispatch_attempts FROM orders WHERE id = ?", Integer.class, order));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'DISPATCH_REQUESTED'", Integer.class, order));
  }
  @Test void driverUnavailableReturnsWaitingOrdersToTheQueue() {
    var s = setup(1, 1); UUID order = s.orderIds().get(0);
    assertTrue(assignment.tryAssign(s.org(), order, s.driver()));
    int[] r = tx.reassignFromDriver(s.org(), s.driver());
    assertArrayEquals(new int[]{1, 0}, r);
    assertEquals("DISPATCH_REQUESTED", jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order));
    assertNull(jdbc.queryForObject("SELECT driver_id FROM orders WHERE id = ?", UUID.class, order));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'DISPATCH_REQUESTED'", Integer.class, order));
  }
}
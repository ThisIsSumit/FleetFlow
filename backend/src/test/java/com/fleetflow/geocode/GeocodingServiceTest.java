package com.fleetflow.geocode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.common.*;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
/** Runs against a local stub server, so it needs neither network nor Redis (cache and limiter fail open without Redis). */
class GeocodingServiceTest {
  HttpServer server; final AtomicReference<String> query = new AtomicReference<>(), ua = new AtomicReference<>(); volatile int status = 200; volatile int calls;
  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/search", ex -> { calls++; query.set(ex.getRequestURI().getRawQuery()); ua.set(ex.getRequestHeaders().getFirst("User-Agent"));
      byte[] b = "[{\"display_name\":\"Sector 17, Chandigarh, India\",\"lat\":\"30.7398\",\"lon\":\"76.7827\"}]".getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(status, b.length); ex.getResponseBody().write(b); ex.close(); });
    server.start();
  }
  @AfterEach void stop() { server.stop(0); }
  GeocodingService svc() { var om = new ObjectMapper(); return new GeocodingService(new RateLimiter(null), new CacheService(null, om, new SimpleMeterRegistry()), om, "http://127.0.0.1:" + server.getAddress().getPort() + "/", "in", "FleetFlow-Test/1.0", 5); }
  @Test void parsesResultsAndSendsIdentifyingUserAgentAndCountryFilter() {
    var r = svc().search("Sector 17 Chandigarh");
    assertEquals(1, r.size()); assertEquals(30.7398, r.get(0).lat(), 1e-6); assertEquals(76.7827, r.get(0).lng(), 1e-6); assertTrue(r.get(0).label().startsWith("Sector 17"));
    assertEquals("FleetFlow-Test/1.0", ua.get()); assertTrue(query.get().contains("countrycodes=in")); assertTrue(query.get().contains("q=Sector+17+Chandigarh"));
  }
  @Test void shortOrEmptyQueriesNeverHitTheUpstream() { assertTrue(svc().search("ab").isEmpty()); assertTrue(svc().search(null).isEmpty()); assertEquals(0, calls); }
  @Test void upstreamFailureBecomesA502() { status = 500; var e = assertThrows(ApiException.class, () -> svc().search("Sector 17")); assertEquals(502, e.status.value()); }
}

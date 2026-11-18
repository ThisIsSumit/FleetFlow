package com.fleetflow.fleet;
import com.fasterxml.jackson.databind.*;
import com.fleetflow.outbox.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class FleetFlowIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
    r.add("fleetflow.rate-limit.signup-per-hour", () -> "10000"); // these tests may share a local Redis across runs
    r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername); r.add("spring.datasource.password", pg::getPassword);
    r.add("fleetflow.jwt.secret", () -> "test-secret-test-secret-test-secret-123456"); r.add("fleetflow.refresh.cookie-secure", () -> "false");
    r.add("fleetflow.outbox.poll-ms", () -> "3600000"); // keep the relay quiet so the test can inspect PENDING rows
  }
  @Autowired MockMvc mvc; @Autowired ObjectMapper om; @Autowired JdbcTemplate jdbc;

  private String signup(String email, String org) throws Exception {
    var r = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"O\",\"email\":\"" + email + "\",\"password\":\"correct-horse-1\",\"organizationName\":\"" + org + "\"}")).andExpect(status().isCreated()).andReturn();
    return "Bearer " + om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
  }
  private JsonNode call(String tok, String url, String body, int expect) throws Exception {
    var r = mvc.perform(post(url).header("Authorization", tok).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expect)).andReturn();
    var s = r.getResponse().getContentAsString(); return s.isEmpty() ? null : om.readTree(s);
  }
  static final String ORDER = "{\"customerName\":\"Asha\",\"pickupAddress\":\"Sector 17, Chandigarh\",\"pickupLat\":30.7412,\"pickupLng\":76.7826,"
      + "\"dropAddress\":\"Phase 5, Mohali\",\"dropLat\":30.7046,\"dropLng\":76.7179,\"items\":[{\"name\":\"Parcel\",\"quantity\":1}],\"priority\":\"HIGH\"}";

  @Test void orderCreationWritesOutboxAtomicallyAndStateMachineIsEnforced() throws Exception {
    String a = signup("fa@x.dev", "Fleet A");
    String id = call(a, "/api/v1/orders", ORDER, 201).get("id").asText();
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?::uuid AND event_type='ORDER_CREATED' AND status='PENDING'", Integer.class, id));
    call(a, "/api/v1/orders/" + id + "/status", "{\"status\":\"DELIVERED\"}", 409);   // CREATED -> DELIVERED illegal
    call(a, "/api/v1/orders/" + id + "/status", "{\"status\":\"ASSIGNED\"}", 400);    // system-controlled
    call(a, "/api/v1/orders/" + id + "/status", "{\"status\":\"CANCELLED\"}", 200);
    mvc.perform(get("/api/v1/orders/" + id).header("Authorization", a)).andExpect(status().isOk()).andExpect(jsonPath("$.timeline.length()").value(2));
  }
  @Test void validationRejectsBadCoordinates() throws Exception {
    String a = signup("fv@x.dev", "Fleet V");
    call(a, "/api/v1/orders", ORDER.replace("30.7412", "130.7412"), 400);
  }
  @Test void tenantsCannotSeeOrUseEachOthersData() throws Exception {
    String a = signup("ta@x.dev", "Tenant A"), b = signup("tb@x.dev", "Tenant B");
    String orderA = call(a, "/api/v1/orders", ORDER, 201).get("id").asText();
    String vehicleA = call(a, "/api/v1/vehicles", "{\"registrationNumber\":\"CH01AB1234\",\"type\":\"BIKE\",\"capacityKg\":20}", 201).get("id").asText();
    mvc.perform(get("/api/v1/orders/" + orderA).header("Authorization", b)).andExpect(status().isNotFound());
    call(b, "/api/v1/orders/" + orderA + "/status", "{\"status\":\"CANCELLED\"}", 404);
    call(b, "/api/v1/drivers", "{\"name\":\"Ravi\",\"vehicleId\":\"" + vehicleA + "\"}", 404);   // cannot attach another tenant's vehicle
    mvc.perform(get("/api/v1/orders").header("Authorization", b)).andExpect(jsonPath("$.totalElements").value(0));
    call(a, "/api/v1/drivers", "{\"name\":\"Ravi\",\"vehicleId\":\"" + vehicleA + "\"}", 201);
    call(a, "/api/v1/drivers", "{\"name\":\"Meena\",\"vehicleId\":\"" + vehicleA + "\"}", 409); // one driver per vehicle
  }
  @Test void dashboardSummaryIsTenantScopedAndHealthIsReported() throws Exception {
    String a = signup("sa@x.dev", "Summary A"), b = signup("sb@x.dev", "Summary B");
    call(a, "/api/v1/orders", ORDER, 201);
    mvc.perform(get("/api/v1/dashboard/summary").header("Authorization", a)).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalOrders").value(1)).andExpect(jsonPath("$.ordersByDay.length()").value(7));
    mvc.perform(get("/api/v1/dashboard/summary").header("Authorization", b)).andExpect(status().isOk()).andExpect(jsonPath("$.totalOrders").value(0));
    mvc.perform(get("/api/v1/system/health").header("Authorization", a)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.name=='Database')].status").value("UP"));
  }
  @Test void relayMarksEventsSent() throws Exception {
    String a = signup("ra@x.dev", "Relay A");
    String id = call(a, "/api/v1/orders", ORDER, 201).get("id").asText();
    relay.drain();
    assertEquals("SENT", jdbc.queryForObject("SELECT status FROM outbox_events WHERE aggregate_id = ?::uuid", String.class, id));
  }
  @Autowired OutboxRelay relay;
}

package com.fleetflow.dispatch;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import java.time.Duration;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** signup -> vehicle -> driver -> location -> order -> outbox -> Kafka -> consumers -> scoring -> assignment. */
@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class DispatchFlowIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
  @Container static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");
  @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername); r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers); r.add("fleetflow.events.publisher", () -> "kafka");
    r.add("fleetflow.outbox.poll-ms", () -> "200");
    r.add("fleetflow.jwt.secret", () -> "test-secret-test-secret-test-secret-123456"); r.add("fleetflow.refresh.cookie-secure", () -> "false");
  }
  @Autowired MockMvc mvc; @Autowired ObjectMapper om;
  private String signup(String email, String org) throws Exception {
    var r = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"O\",\"email\":\"" + email + "\",\"password\":\"correct-horse-1\",\"organizationName\":\"" + org + "\"}")).andReturn();
    return "Bearer " + om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
  }
  private JsonNode call(String method, String tok, String url, String body) throws Exception {
    var b = method.equals("PATCH") ? patch(url) : post(url);
    var r = mvc.perform(b.header("Authorization", tok).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is2xxSuccessful()).andReturn();
    var s = r.getResponse().getContentAsString(); return s.isEmpty() ? null : om.readTree(s);
  }
  private String makeDriver(String tok, String name, String reg, double lat, double lng) throws Exception {
    String vid = call("POST", tok, "/api/v1/vehicles", "{\"registrationNumber\":\"" + reg + "\",\"type\":\"BIKE\",\"capacityKg\":20}").get("id").asText();
    String did = call("POST", tok, "/api/v1/drivers", "{\"name\":\"" + name + "\",\"vehicleId\":\"" + vid + "\"}").get("id").asText();
    call("PATCH", tok, "/api/v1/drivers/" + did + "/status", "{\"status\":\"AVAILABLE\"}");
    call("POST", tok, "/api/v1/drivers/" + did + "/location", "{\"latitude\":" + lat + ",\"longitude\":" + lng + "}");
    return did;
  }
  @Test void orderIsDispatchedToNearestEligibleDriverInSameTenantOnly() throws Exception {
    String a = signup("da@x.dev", "Dispatch A"), b = signup("db@x.dev", "Dispatch B");
    String nearA = makeDriver(a, "Near", "CH01NEAR", 30.7420, 76.7830);
    makeDriver(a, "Far", "CH01FAR", 30.7700, 76.8100);          // ~4 km away
    makeDriver(b, "Other tenant", "CH01B", 30.7412, 76.7826);   // closest of all, but a different organization
    String order = call("POST", a, "/api/v1/orders", "{\"customerName\":\"Asha\",\"pickupAddress\":\"Sector 17\",\"pickupLat\":30.7412,\"pickupLng\":76.7826,"
            + "\"dropAddress\":\"Mohali\",\"dropLat\":30.7046,\"dropLng\":76.7179,\"items\":[{\"name\":\"Parcel\",\"quantity\":1}],\"priority\":\"HIGH\",\"vehicleRequirement\":\"BIKE\"}").get("id").asText();
    await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
            mvc.perform(get("/api/v1/orders/" + order).header("Authorization", a)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.order.status").value("ASSIGNED")).andExpect(jsonPath("$.order.driverId").value(nearA))
                    .andExpect(jsonPath("$.timeline.length()").value(3)));
  }
  @Test void healthReportsKafkaAndConsumers() throws Exception {
    String a = signup("hk@x.dev", "Health K");
    mvc.perform(get("/api/v1/system/health").header("Authorization", a)).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name=='Kafka')].status").value("UP")).andExpect(jsonPath("$[?(@.name=='Dispatch')].status").value("UP"));
  }
  @Test void driverGoingOfflineSendsTheWaitingOrderToAnotherDriver() throws Exception {
    String a = signup("of@x.dev", "Offline Org");
    String near = makeDriver(a, "Near", "CH02NEAR", 30.7420, 76.7830), far = makeDriver(a, "Far", "CH02FAR", 30.7700, 76.8100);
    String order = call("POST", a, "/api/v1/orders", "{\"customerName\":\"Asha\",\"pickupAddress\":\"Sector 17\",\"pickupLat\":30.7412,\"pickupLng\":76.7826,"
            + "\"dropAddress\":\"Mohali\",\"dropLat\":30.7046,\"dropLng\":76.7179,\"items\":[{\"name\":\"Parcel\",\"quantity\":1}],\"priority\":\"HIGH\",\"vehicleRequirement\":\"BIKE\"}").get("id").asText();
    await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
            mvc.perform(get("/api/v1/orders/" + order).header("Authorization", a)).andExpect(jsonPath("$.order.driverId").value(near)));
    call("PATCH", a, "/api/v1/drivers/" + near + "/status", "{\"status\":\"OFFLINE\"}");
    await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
            mvc.perform(get("/api/v1/orders/" + order).header("Authorization", a)).andExpect(jsonPath("$.order.status").value("ASSIGNED")).andExpect(jsonPath("$.order.driverId").value(far)));
  }
}
package com.fleetflow.account;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @Import(AccountFlowIT.Capture.class)
class AccountFlowIT {
  @TestConfiguration static class Capture {
    static final List<String[]> MAILS = new CopyOnWriteArrayList<>();
    @Bean @Primary MailService capturingMail() { return (to, subject, body) -> MAILS.add(new String[]{to, body}); }
  }
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
    r.add("fleetflow.rate-limit.signup-per-hour", () -> "10000"); // these tests may share a local Redis across runs
    r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername); r.add("spring.datasource.password", pg::getPassword);
    r.add("fleetflow.jwt.secret", () -> "test-secret-test-secret-test-secret-123456"); r.add("fleetflow.refresh.cookie-secure", () -> "false");
    r.add("fleetflow.outbox.poll-ms", () -> "3600000");
  }
  @Autowired MockMvc mvc; @Autowired ObjectMapper om; @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  static String tokenFor(String to) { var m = Pattern.compile("token=([A-Za-z0-9_-]+)"); for (int i = Capture.MAILS.size() - 1; i >= 0; i--) if (Capture.MAILS.get(i)[0].equalsIgnoreCase(to)) { var x = m.matcher(Capture.MAILS.get(i)[1]); if (x.find()) return x.group(1); } throw new AssertionError("no mail for " + to); }
  MvcResult json(String url, String body, String auth, int expect) throws Exception {
    var b = post(url).contentType(MediaType.APPLICATION_JSON).content(body); if (auth != null) b.header("Authorization", auth);
    return mvc.perform(b).andExpect(status().is(expect)).andReturn();
  }
  String signup(String email, String org) throws Exception {
    var r = json("/api/v1/auth/signup", "{\"name\":\"O\",\"email\":\"" + email + "\",\"password\":\"correct-horse-1\",\"organizationName\":\"" + org + "\"}", null, 201);
    return "Bearer " + om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
  }
  @Test void verifyEmailIsSingleUse() throws Exception {
    String a = signup("v@x.dev", "V Org"); String t = tokenFor("v@x.dev");
    json("/api/v1/auth/verify-email", "{\"token\":\"" + t + "\"}", null, 204);
    mvc.perform(get("/api/v1/me").header("Authorization", a)).andExpect(jsonPath("$.emailVerified").value(true));
    json("/api/v1/auth/verify-email", "{\"token\":\"" + t + "\"}", null, 400);
  }
  @Test void passwordResetChangesPasswordAndRevokesSessions() throws Exception {
    var s = json("/api/v1/auth/signup", "{\"name\":\"O\",\"email\":\"p@x.dev\",\"password\":\"correct-horse-1\",\"organizationName\":\"P Org\"}", null, 201);
    Cookie refresh = s.getResponse().getCookie("ff_refresh");
    json("/api/v1/auth/forgot-password", "{\"email\":\"nobody@x.dev\"}", null, 204);                  // same answer for unknown emails
    json("/api/v1/auth/forgot-password", "{\"email\":\"p@x.dev\"}", null, 204);
    String t = tokenFor("p@x.dev");
    json("/api/v1/auth/reset-password", "{\"token\":\"" + t + "\",\"newPassword\":\"new-password-99\"}", null, 204);
    json("/api/v1/auth/reset-password", "{\"token\":\"" + t + "\",\"newPassword\":\"another-pass-99\"}", null, 400); // single use
    mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());               // old sessions are gone
    json("/api/v1/auth/login", "{\"email\":\"p@x.dev\",\"password\":\"correct-horse-1\"}", null, 401);
    json("/api/v1/auth/login", "{\"email\":\"p@x.dev\",\"password\":\"new-password-99\"}", null, 200);
  }
  @Test void invitationFlowAndRolePermissions() throws Exception {
    String owner = signup("o@x.dev", "Invite Org");
    json("/api/v1/team/invitations", "{\"email\":\"d@x.dev\",\"role\":\"DISPATCHER\"}", owner, 201);
    json("/api/v1/team/invitations", "{\"email\":\"x@x.dev\",\"role\":\"OWNER\"}", owner, 403);            // ownership cannot be granted
    var acc = json("/api/v1/auth/accept-invitation", "{\"token\":\"" + tokenFor("d@x.dev") + "\",\"name\":\"Dee\",\"password\":\"dispatcher-pass-1\"}", null, 201);
    String disp = "Bearer " + om.readTree(acc.getResponse().getContentAsString()).get("accessToken").asText();
    mvc.perform(get("/api/v1/team").header("Authorization", disp)).andExpect(status().isForbidden());        // dispatcher cannot manage the team
    var team = om.readTree(mvc.perform(get("/api/v1/team").header("Authorization", owner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertEquals(2, team.size());
    String dispId = null; for (var m : team) if (m.get("email").asText().equals("d@x.dev")) dispId = m.get("id").asText();
    mvc.perform(patch("/api/v1/team/" + dispId + "/enabled").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
    json("/api/v1/auth/login", "{\"email\":\"d@x.dev\",\"password\":\"dispatcher-pass-1\"}", null, 401);     // disabled users cannot log in
  }
  static final String ORDER = "{\"customerName\":\"Asha\",\"pickupAddress\":\"Sector 17\",\"pickupLat\":30.7412,\"pickupLng\":76.7826,\"dropAddress\":\"Mohali\",\"dropLat\":30.7046,\"dropLng\":76.7179,\"items\":[{\"name\":\"Parcel\",\"quantity\":1}],\"priority\":\"HIGH\"}";
  @Test void driverPortalEnforcesOwnershipAndAllowedStatuses() throws Exception {
    String owner = signup("po@x.dev", "Portal Org");
    String driverId = om.readTree(json("/api/v1/drivers", "{\"name\":\"Ravi\"}", owner, 201).getResponse().getContentAsString()).get("id").asText();
    json("/api/v1/team/invitations", "{\"email\":\"drv@x.dev\",\"role\":\"DRIVER\",\"driverId\":\"" + driverId + "\"}", owner, 201);
    json("/api/v1/team/invitations", "{\"email\":\"drv2@x.dev\",\"role\":\"DRIVER\",\"driverId\":\"" + driverId + "\"}", owner, 201); // second invite for the same driver is allowed until one is accepted
    var acc = json("/api/v1/auth/accept-invitation", "{\"token\":\"" + tokenFor("drv@x.dev") + "\",\"name\":\"Ravi\",\"password\":\"driver-pass-123\"}", null, 201);
    String drv = "Bearer " + om.readTree(acc.getResponse().getContentAsString()).get("accessToken").asText();
    json("/api/v1/auth/accept-invitation", "{\"token\":\"" + tokenFor("drv2@x.dev") + "\",\"name\":\"Ravi Two\",\"password\":\"driver-pass-123\"}", null, 201); // creates a user, but the driver is already linked
    mvc.perform(get("/api/v1/driver/me").header("Authorization", drv)).andExpect(status().isOk()).andExpect(jsonPath("$.driver.id").value(driverId)).andExpect(jsonPath("$.driver.hasLogin").value(true));
    String mine = om.readTree(json("/api/v1/orders", ORDER, owner, 201).getResponse().getContentAsString()).get("id").asText();
    String other = om.readTree(json("/api/v1/orders", ORDER, owner, 201).getResponse().getContentAsString()).get("id").asText();
    jdbc.update("UPDATE orders SET status = 'ASSIGNED', driver_id = ?::uuid WHERE id = ?::uuid", driverId, mine);
    json("/api/v1/driver/orders/" + mine + "/status", "{\"status\":\"ARRIVED\"}", drv, 200);
    json("/api/v1/driver/orders/" + other + "/status", "{\"status\":\"ARRIVED\"}", drv, 404);      // not theirs: indistinguishable from missing
    json("/api/v1/driver/orders/" + mine + "/status", "{\"status\":\"CANCELLED\"}", drv, 403);    // drivers cannot cancel
    mvc.perform(patch("/api/v1/driver/status").header("Authorization", drv).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OFFLINE\"}")).andExpect(status().isConflict()); // active delivery
    mvc.perform(get("/api/v1/orders").header("Authorization", drv)).andExpect(status().isForbidden());  // no staff API access
    mvc.perform(get("/api/v1/driver/me").header("Authorization", owner)).andExpect(status().isForbidden()); // staff are not drivers
  }
}

package com.fleetflow.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import jakarta.servlet.http.Cookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class AuthFlowIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
    r.add("fleetflow.rate-limit.signup-per-hour", () -> "10000"); // these tests may share a local Redis across runs
    r.add("spring.datasource.url", pg::getJdbcUrl); r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("fleetflow.jwt.secret", () -> "test-secret-test-secret-test-secret-123456");
    r.add("fleetflow.refresh.cookie-secure", () -> "false");
    r.add("fleetflow.refresh.reuse-grace-seconds", () -> "1");
  }
  @Autowired MockMvc mvc; @Autowired ObjectMapper om;

  private MvcResult signup(String email, String org) throws Exception {
    return mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"A\",\"email\":\"" + email + "\",\"password\":\"correct-horse-1\",\"organizationName\":\"" + org + "\"}"))
        .andExpect(status().isCreated()).andReturn();
  }
  private String token(MvcResult r) throws Exception { return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText(); }

  @Test void refreshRotatesAndReuseRevokesEverything() throws Exception {
    var s = signup("rot@x.dev", "Rot Org");
    Cookie old = s.getResponse().getCookie("ff_refresh");
    assertTrue(old.isHttpOnly());
    var r1 = mvc.perform(post("/api/v1/auth/refresh").cookie(old)).andExpect(status().isOk()).andReturn();
    Cookie fresh = r1.getResponse().getCookie("ff_refresh");
    assertNotEquals(old.getValue(), fresh.getValue());
    Thread.sleep(1500);                                                                             // past the reuse grace window
    mvc.perform(post("/api/v1/auth/refresh").cookie(old)).andExpect(status().isUnauthorized());   // replay detected
    mvc.perform(post("/api/v1/auth/refresh").cookie(fresh)).andExpect(status().isUnauthorized()); // family revoked
  }

  @Test void concurrentRefreshWithinGraceIsNotTreatedAsTheft() throws Exception {
    Cookie old = signup("tabs@x.dev", "Tabs Org").getResponse().getCookie("ff_refresh");
    mvc.perform(post("/api/v1/auth/refresh").cookie(old)).andExpect(status().isOk());              // tab 1
    var r2 = mvc.perform(post("/api/v1/auth/refresh").cookie(old)).andExpect(status().isOk()).andReturn(); // tab 2, same cookie
    mvc.perform(post("/api/v1/auth/refresh").cookie(r2.getResponse().getCookie("ff_refresh"))).andExpect(status().isOk()); // session survives
  }

  @Test void tenantIsolationAndRbac() throws Exception {
    String a = token(signup("a@x.dev", "Org A")); signup("b@x.dev", "Org B");
    mvc.perform(get("/api/v1/team").header("Authorization", "Bearer " + a))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].email").value("a@x.dev"));
    mvc.perform(get("/api/v1/team")).andExpect(status().isUnauthorized());
  }
}

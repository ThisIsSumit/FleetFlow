package com.fleetflow.account;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EmailHtmlTest {
  @Test void turnsTheLinkIntoAButtonAndKeepsBranding() {
    String h = EmailHtml.render("Reset your FleetFlow password", "Choose a new password (valid 30 minutes): http://localhost:5173/reset-password?token=abc_DEF-1");
    assertTrue(h.contains("<a href=\"http://localhost:5173/reset-password?token=abc_DEF-1\"")); assertTrue(h.contains("FleetFlow")); assertTrue(h.contains("Choose a new password"));
  }
  @Test void escapesHtmlSoTenantDataCannotInjectMarkup() {
    String h = EmailHtml.render("<b>x</b>", "Hello <script>alert(1)</script> & welcome https://a.example/x?a=1&b=2");
    assertFalse(h.contains("<script>")); assertFalse(h.contains("<b>x</b>")); assertTrue(h.contains("&lt;script&gt;")); assertTrue(h.contains("a=1&amp;b=2"));
  }
}

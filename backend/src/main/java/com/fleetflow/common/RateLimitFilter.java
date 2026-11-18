package com.fleetflow.common;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetflow.security.AuthPrincipal;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.*;
import java.util.List;
import java.util.regex.*;
/** Runs after Spring Security, so the authenticated user is known for per-user limits.
 *  IP-based keys use getRemoteAddr(): behind a reverse proxy set FORWARD_HEADERS_STRATEGY=native|framework so it is the client IP. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
  private static final Pattern LOCATION = Pattern.compile("^/api/v1/drivers/([0-9a-fA-F-]{36})/location$");
  private record Rule(String key, int limit, Duration window) {}
  private final RateLimiter limiter; private final ObjectMapper om; private final int login, orders, location, signupPerHour, tokenLinks, refresh;
  public RateLimitFilter(RateLimiter limiter, ObjectMapper om, @Value("${fleetflow.rate-limit.login-per-minute}") int login,
      @Value("${fleetflow.rate-limit.orders-per-minute}") int orders, @Value("${fleetflow.rate-limit.location-per-minute}") int location,
      @Value("${fleetflow.rate-limit.signup-per-hour:10}") int signupPerHour, @Value("${fleetflow.rate-limit.token-links-per-minute:10}") int tokenLinks,
      @Value("${fleetflow.rate-limit.refresh-per-minute:60}") int refresh) {
    this.limiter = limiter; this.om = om; this.login = login; this.orders = orders; this.location = location;
    this.signupPerHour = signupPerHour; this.tokenLinks = tokenLinks; this.refresh = refresh;
  }
  private static AuthPrincipal user() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a != null && a.getPrincipal() instanceof AuthPrincipal p ? p : null;
  }
  private Rule rule(HttpServletRequest req) {
    if (!"POST".equals(req.getMethod())) return null;
    String p = req.getRequestURI(), ip = req.getRemoteAddr(); var u = user(); Duration minute = Duration.ofMinutes(1);
    switch (p) {
      case "/api/v1/auth/login": return new Rule("rl:login:" + ip, login, minute);
      case "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password": return new Rule("rl:pwd:" + ip, login, minute);
      case "/api/v1/auth/signup": return new Rule("rl:signup:" + ip, signupPerHour, Duration.ofHours(1));
      case "/api/v1/auth/verify-email", "/api/v1/auth/accept-invitation": return new Rule("rl:link:" + ip, tokenLinks, minute);
      case "/api/v1/auth/refresh": return new Rule("rl:refresh:" + ip, refresh, minute);
      case "/api/v1/auth/resend-verification": return u == null ? null : new Rule("rl:resend:" + u.userId(), 3, Duration.ofMinutes(10));
      case "/api/v1/orders": return u == null ? null : new Rule("rl:orders:" + u.userId(), orders, minute);
      case "/api/v1/driver/location": return u == null ? null : new Rule("rl:loc:user:" + u.userId(), location, minute);
      default: { Matcher m = LOCATION.matcher(p); return m.matches() ? new Rule("rl:loc:" + m.group(1), location, minute) : null; }
    }
  }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    var r = rule(req);
    if (r != null) {
      var d = limiter.hit(r.key(), r.limit(), r.window());
      if (!d.allowed()) {
        res.setStatus(429); res.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds())); res.setContentType("application/json");
        om.writeValue(res.getOutputStream(), new GlobalExceptionHandler.ErrorBody(Instant.now(), 429, "RATE_LIMITED", "Too many requests. Retry in " + d.retryAfterSeconds() + "s.",
            req.getRequestURI(), MDC.get(CorrelationFilter.KEY), List.of()));
        return;
      }
    }
    chain.doFilter(req, res);
  }
}

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
/** Runs after Spring Security, so the authenticated user is known for per-user limits. */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
  private static final Pattern LOCATION = Pattern.compile("^/api/v1/drivers/([0-9a-fA-F-]{36})/location$");
  private final RateLimiter limiter; private final ObjectMapper om; private final int login, orders, location;
  public RateLimitFilter(RateLimiter limiter, ObjectMapper om, @Value("${fleetflow.rate-limit.login-per-minute}") int login,
      @Value("${fleetflow.rate-limit.orders-per-minute}") int orders, @Value("${fleetflow.rate-limit.location-per-minute}") int location) {
    this.limiter = limiter; this.om = om; this.login = login; this.orders = orders; this.location = location;
  }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String key = null; int limit = 0;
    if ("POST".equals(req.getMethod())) {
      String p = req.getRequestURI();
      if (p.equals("/api/v1/auth/login")) { key = "rl:login:" + req.getRemoteAddr(); limit = login; }   // behind a proxy, configure forwarded headers so this is the client IP
      else if (p.equals("/api/v1/auth/forgot-password") || p.equals("/api/v1/auth/reset-password")) { key = "rl:pwd:" + req.getRemoteAddr(); limit = login; }
      else if (p.equals("/api/v1/orders") && SecurityContextHolder.getContext().getAuthentication() != null
          && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AuthPrincipal u) { key = "rl:orders:" + u.userId(); limit = orders; }
      else if (p.equals("/api/v1/driver/location") && SecurityContextHolder.getContext().getAuthentication() != null
          && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof AuthPrincipal d) { key = "rl:loc:user:" + d.userId(); limit = location; }
      else { Matcher m = LOCATION.matcher(p); if (m.matches()) { key = "rl:loc:" + m.group(1); limit = location; } }
    }
    if (key != null) {
      var d = limiter.hit(key, limit, Duration.ofMinutes(1));
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

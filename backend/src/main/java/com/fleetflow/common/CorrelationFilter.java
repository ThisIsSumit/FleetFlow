package com.fleetflow.common;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
  public static final String KEY = "correlationId";
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String h = req.getHeader("X-Correlation-Id");
    String id = (h != null && h.matches("[A-Za-z0-9-]{8,64}")) ? h : UUID.randomUUID().toString();
    MDC.put(KEY, id); res.setHeader("X-Correlation-Id", id);
    try { chain.doFilter(req, res); } finally { MDC.remove(KEY); }
  }
}

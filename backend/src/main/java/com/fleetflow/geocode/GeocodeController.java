package com.fleetflow.geocode;
import com.fleetflow.common.*;
import com.fleetflow.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.List;
@RestController @RequestMapping("/api/v1/geocode") @PreAuthorize("hasAnyRole('OWNER','ADMIN','DISPATCHER')")
public class GeocodeController {
  private final GeocodingService svc; private final RateLimiter limiter;
  public GeocodeController(GeocodingService svc, RateLimiter limiter) { this.svc = svc; this.limiter = limiter; }
  @GetMapping public List<GeocodingService.Place> search(@RequestParam String q) {
    if (!limiter.hit("rl:geo:user:" + TenantContext.current().userId(), 30, Duration.ofMinutes(1)).allowed()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many address searches");
    return svc.search(q);
  }
}

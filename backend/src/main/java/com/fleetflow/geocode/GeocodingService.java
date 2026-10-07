package com.fleetflow.geocode;
import com.fasterxml.jackson.databind.*;
import com.fleetflow.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
/** Address search via any Nominatim-compatible API (GEOCODER_URL). Results are cached 24 h and upstream calls are throttled globally.
 *  The public nominatim.openstreetmap.org is for light development use only (1 request/second, identifying User-Agent required);
 *  use your own instance or a commercial provider in production. */
@Service
public class GeocodingService {
  public record Place(String label, double lat, double lng) {}
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final RateLimiter limiter; private final CacheService cache; private final ObjectMapper om; private final String base, countries, userAgent; private final int maxPerSecond;
  public GeocodingService(RateLimiter limiter, CacheService cache, ObjectMapper om, @Value("${fleetflow.geocoder.base-url}") String base, @Value("${fleetflow.geocoder.country-codes}") String countries,
      @Value("${fleetflow.geocoder.user-agent}") String userAgent, @Value("${fleetflow.geocoder.max-per-second}") int maxPerSecond) {
    this.limiter = limiter; this.cache = cache; this.om = om; this.base = base.replaceAll("/+$", ""); this.countries = countries; this.userAgent = userAgent; this.maxPerSecond = maxPerSecond;
  }
  static String sha(String s) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
  public List<Place> search(String q) {
    String query = q == null ? "" : q.trim(); if (query.length() < 3 || query.length() > 200) return List.of();
    Place[] r = cache.get("geocode", sha(query.toLowerCase()), Place[].class, Duration.ofHours(24), () -> fetch(query));
    return r == null ? List.of() : List.of(r);
  }
  private Place[] fetch(String q) {
    if (!limiter.hit("rl:geocode:upstream", maxPerSecond, Duration.ofSeconds(1)).allowed()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "GEOCODER_BUSY", "Address search is busy, try again in a moment");
    try {
      var uri = URI.create(base + "/search?format=jsonv2&limit=5&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8) + (countries.isBlank() ? "" : "&countrycodes=" + URLEncoder.encode(countries, StandardCharsets.UTF_8)));
      var res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4)).header("User-Agent", userAgent).header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) throw new ApiException(HttpStatus.BAD_GATEWAY, "GEOCODER_UNAVAILABLE", "Address search is unavailable");
      var out = new ArrayList<Place>();
      for (JsonNode n : om.readTree(res.body())) {
        String label = n.path("display_name").asText(""); if (label.length() > 200) label = label.substring(0, 200);
        out.add(new Place(label, Double.parseDouble(n.path("lat").asText()), Double.parseDouble(n.path("lon").asText())));
      }
      return out.toArray(new Place[0]);
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.BAD_GATEWAY, "GEOCODER_UNAVAILABLE", "Address search is unavailable");
    } catch (IOException | RuntimeException e) { if (e instanceof ApiException a) throw a; throw new ApiException(HttpStatus.BAD_GATEWAY, "GEOCODER_UNAVAILABLE", "Address search is unavailable"); }
  }
}

package com.fleetflow.security;
import com.fleetflow.domain.*;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
@Service
public class JwtService {
  private final SecretKey key; private final long ttl;
  public JwtService(@Value("${fleetflow.jwt.secret}") String secret, @Value("${fleetflow.jwt.access-ttl-seconds}") long ttl) {
    byte[] b = secret.getBytes(StandardCharsets.UTF_8);
    if (b.length < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
    this.key = Keys.hmacShaKeyFor(b); this.ttl = ttl;
  }
  public long ttlSeconds() { return ttl; }
  public String create(AppUser u, UUID sessionId) {
    Instant now = Instant.now();
    return Jwts.builder().subject(u.getId().toString()).claim("org", u.getOrganizationId().toString())
        .claim("role", u.getRole().name()).claim("sid", sessionId.toString())
        .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttl))).signWith(key).compact();
  }
  /** Verifies the token and returns when it expires (used to close WebSockets opened with it). */
  public Instant expiry(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getExpiration().toInstant(); }
  public AuthPrincipal parse(String token) {
    Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    return new AuthPrincipal(UUID.fromString(c.getSubject()), UUID.fromString(c.get("org", String.class)),
        Role.valueOf(c.get("role", String.class)), UUID.fromString(c.get("sid", String.class)));
  }
}

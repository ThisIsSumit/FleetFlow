package com.fleetflow.account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
/** Single-use, expiring tokens. Only the SHA-256 is stored; the raw token exists only in the email. */
@Service
public class AuthTokenService {
  public record Token(UUID orgId, UUID userId, String email, String role, UUID driverId) {}
  private static final SecureRandom RNG = new SecureRandom();
  private final JdbcTemplate jdbc; public AuthTokenService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  static String hash(String raw) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  public String create(String type, UUID orgId, UUID userId, String email, String role, Duration ttl) { return create(type, orgId, userId, email, role, ttl, null); }
  public String create(String type, UUID orgId, UUID userId, String email, String role, Duration ttl, UUID driverId) {
    if (userId != null) jdbc.update("UPDATE auth_tokens SET used_at = now() WHERE user_id = ? AND type = ? AND used_at IS NULL", userId, type); // newest link wins
    byte[] b = new byte[32]; RNG.nextBytes(b); String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    jdbc.update("INSERT INTO auth_tokens(id, type, token_hash, organization_id, user_id, email, role, expires_at, driver_id) VALUES (?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(), type, hash(raw), orgId, userId, email, role, java.sql.Timestamp.from(Instant.now().plus(ttl)), driverId);
    return raw;
  }
  /** Atomic consume: the UPDATE ... RETURNING guarantees a token can be used exactly once even under concurrent requests. */
  public Optional<Token> consume(String raw, String type) {
    if (raw == null || raw.isBlank()) return Optional.empty();
    return jdbc.query("UPDATE auth_tokens SET used_at = now() WHERE token_hash = ? AND type = ? AND used_at IS NULL AND expires_at > now() RETURNING organization_id, user_id, email, role, driver_id",
        (rs, i) -> new Token(rs.getObject("organization_id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("email"), rs.getString("role"), rs.getObject("driver_id", UUID.class)), hash(raw), type).stream().findFirst();
  }
}

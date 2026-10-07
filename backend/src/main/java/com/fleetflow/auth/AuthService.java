package com.fleetflow.auth;
import com.fleetflow.auth.AuthDtos.*;
import com.fleetflow.common.ApiException;
import com.fleetflow.domain.*;
import com.fleetflow.domain.Repos.*;
import com.fleetflow.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
@Service
public class AuthService {
  public record Issued(AuthResponse body, String refreshToken, long refreshTtlSeconds) {}
  private static final SecureRandom RNG = new SecureRandom();
  // Precomputed hash so unknown-email logins cost the same as wrong-password logins.
  private final String dummyHash;
  private final OrganizationRepository orgs; private final UserRepository users; private final SessionRepository sessions;
  private final PasswordEncoder encoder; private final JwtService jwt; private final long refreshDays;
  public AuthService(OrganizationRepository orgs, UserRepository users, SessionRepository sessions, PasswordEncoder encoder,
      JwtService jwt, @Value("${fleetflow.refresh.ttl-days}") long refreshDays) {
    this.orgs = orgs; this.users = users; this.sessions = sessions; this.encoder = encoder; this.jwt = jwt; this.refreshDays = refreshDays;
    this.dummyHash = encoder.encode("dummy-password-for-timing");
  }
  static String newToken() { byte[] b = new byte[32]; RNG.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  static String sha256(String s) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private Instant expiry() { return Instant.now().plus(Duration.ofDays(refreshDays)); }
  private ApiException unauthorized(String m) { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", m); }

  public Issued issue(AppUser u, String orgName, String ua, String ip) {
    String raw = newToken();
    var s = sessions.save(new RefreshSession(u.getId(), u.getOrganizationId(), sha256(raw), trunc(ua, 400), ip, expiry()));
    return build(u, orgName, s, raw);
  }
  private Issued build(AppUser u, String orgName, RefreshSession s, String raw) {
    var dto = new UserDto(u.getId(), u.getOrganizationId(), orgName, u.getName(), u.getEmail(), u.getRole(), u.isEmailVerified());
    return new Issued(new AuthResponse(jwt.create(u, s.getId()), jwt.ttlSeconds(), dto), raw, Duration.ofDays(refreshDays).toSeconds());
  }
  private static String trunc(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
  private String orgName(UUID id) { return orgs.findById(id).map(Organization::getName).orElse(""); }

  @Transactional
  public Issued signup(SignupRequest r, String ua, String ip) {
    if (users.existsByEmailIgnoreCase(r.email())) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already registered");
    var org = orgs.save(new Organization(r.organizationName().trim()));
    var owner = users.save(new AppUser(org.getId(), r.name().trim(), r.email(), encoder.encode(r.password()), Role.OWNER));
    return issue(owner, org.getName(), ua, ip);
  }

  @Transactional
  public Issued login(LoginRequest r, String ua, String ip) {
    var u = users.findByEmailIgnoreCase(r.email()).orElse(null);
    boolean ok = encoder.matches(r.password(), u != null ? u.getPasswordHash() : dummyHash);
    if (u == null || !ok || !u.isEnabled()) throw unauthorized("Invalid credentials");
    return issue(u, orgName(u.getOrganizationId()), ua, ip);
  }

  /** Rotation with reuse detection: replaying an already-rotated token revokes every session of that user. */
  @Transactional(noRollbackFor = ApiException.class)
  public Issued refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) throw unauthorized("Missing refresh token");
    String h = sha256(rawToken);
    var s = sessions.findByTokenHash(h).orElse(null);
    if (s == null) {
      sessions.findByPreviousTokenHash(h).ifPresent(stolen -> sessions.deleteByUserId(stolen.getUserId()));
      throw unauthorized("Invalid refresh token");
    }
    if (s.getExpiresAt().isBefore(Instant.now())) { sessions.delete(s); throw unauthorized("Refresh token expired"); }
    var u = users.findByIdAndOrganizationId(s.getUserId(), s.getOrganizationId()).filter(AppUser::isEnabled)
        .orElseThrow(() -> unauthorized("User unavailable"));
    String next = newToken();
    s.rotate(sha256(next), expiry());
    sessions.save(s);
    return build(u, orgName(u.getOrganizationId()), s, next);
  }

  @Transactional public void logout(String rawToken) { if (rawToken != null) sessions.findByTokenHash(sha256(rawToken)).ifPresent(sessions::delete); }
  @Transactional public void logoutAll(UUID userId) { sessions.deleteByUserId(userId); }

  public List<SessionDto> sessions(UUID userId, UUID currentSid) {
    return sessions.findByUserIdOrderByLastUsedAtDesc(userId).stream()
        .map(s -> new SessionDto(s.getId(), s.getUserAgent(), s.getIp(), s.getCreatedAt(), s.getLastUsedAt(), s.getId().equals(currentSid))).toList();
  }
  @Transactional
  public void revoke(UUID userId, UUID sid) {
    // scoped by userId: a user can never revoke someone else's session
    sessions.delete(sessions.findByIdAndUserId(sid, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Session not found")));
  }
  public UserDto me(UUID userId, UUID orgId) {
    var u = users.findByIdAndOrganizationId(userId, orgId).orElseThrow(() -> unauthorized("User unavailable"));
    return new UserDto(u.getId(), orgId, orgName(orgId), u.getName(), u.getEmail(), u.getRole(), u.isEmailVerified());
  }
}

package com.fleetflow.account;
import com.fleetflow.auth.AuthService;
import com.fleetflow.common.ApiException;
import com.fleetflow.domain.*;
import com.fleetflow.domain.Repos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.util.UUID;
@Service
public class AccountService {
  @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @org.springframework.beans.factory.annotation.Autowired private com.fleetflow.security.SessionRevocation revocation;
  private final UserRepository users; private final OrganizationRepository orgs; private final SessionRepository sessions;
  private final AuthTokenService tokens; private final MailService mail; private final PasswordEncoder enc; private final AuthService auth; private final String appUrl;
  public AccountService(UserRepository u, OrganizationRepository o, SessionRepository s, AuthTokenService t, MailService m, PasswordEncoder e, AuthService a, @Value("${fleetflow.app-url}") String appUrl) {
    users = u; orgs = o; sessions = s; tokens = t; mail = m; enc = e; auth = a; this.appUrl = appUrl;
  }
  private static ApiException bad() { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "This link is invalid or has expired"); }

  @Transactional public void sendVerification(UUID userId) {
    var u = users.findById(userId).orElse(null); if (u == null || u.isEmailVerified()) return;
    String raw = tokens.create("EMAIL_VERIFY", u.getOrganizationId(), u.getId(), u.getEmail(), null, Duration.ofHours(24));
    mail.sendAfterCommit(u.getEmail(), "Verify your FleetFlow email", "Confirm your email: " + appUrl + "/verify-email?token=" + raw);
  }
  @Transactional public void verifyEmail(String raw) {
    var t = tokens.consume(raw, "EMAIL_VERIFY").orElseThrow(AccountService::bad);
    users.findById(t.userId()).ifPresent(u -> { u.setEmailVerified(true); users.save(u); });
  }
  /** Always looks successful to the caller so the endpoint cannot be used to discover which emails have accounts. */
  @Transactional public void forgotPassword(String email) {
    users.findByEmailIgnoreCase(email).filter(AppUser::isEnabled).ifPresent(u -> {
      String raw = tokens.create("PASSWORD_RESET", u.getOrganizationId(), u.getId(), u.getEmail(), null, Duration.ofMinutes(30));
      mail.sendAfterCommit(u.getEmail(), "Reset your FleetFlow password", "Choose a new password (valid 30 minutes): " + appUrl + "/reset-password?token=" + raw);
    });
  }
  @Transactional public void resetPassword(String raw, String newPassword) {
    var t = tokens.consume(raw, "PASSWORD_RESET").orElseThrow(AccountService::bad);
    var u = users.findById(t.userId()).orElseThrow(AccountService::bad);
    u.setPasswordHash(enc.encode(newPassword)); users.save(u);
    revocation.revokeUser(u.getId()); // a reset signs out every device, including already-issued access tokens
  }
  @Transactional public AuthService.Issued acceptInvitation(String raw, String name, String password, String ua, String ip) {
    var t = tokens.consume(raw, "INVITATION").orElseThrow(AccountService::bad);
    if (users.existsByEmailIgnoreCase(t.email())) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already registered");
    var u = new AppUser(t.orgId(), name.trim(), t.email(), enc.encode(password), Role.valueOf(t.role()));
    u.setEmailVerified(true); // receiving the invitation proves control of the address
    users.saveAndFlush(u); // flush before the JDBC update below: drivers.user_id has a foreign key to this row
    if (t.driverId() != null) jdbc.update("UPDATE drivers SET user_id = ? WHERE id = ? AND organization_id = ? AND user_id IS NULL", u.getId(), t.driverId(), t.orgId());
    return auth.issue(u, orgs.findById(t.orgId()).map(Organization::getName).orElse(""), ua, ip);
  }
}

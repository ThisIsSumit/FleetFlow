package com.fleetflow.account;
import com.fleetflow.common.ApiException;
import com.fleetflow.domain.*;
import com.fleetflow.domain.Repos.*;
import com.fleetflow.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
public class TeamService {
  public record Member(UUID id, String name, String email, Role role, boolean enabled, boolean emailVerified) {}
  public record Invitation(UUID id, String email, String role, Instant expiresAt) {}
  private final UserRepository users; private final SessionRepository sessions; private final AuthTokenService tokens; private final MailService mail;
  private final JdbcTemplate jdbc; private final OrganizationRepository orgs; private final String appUrl;
  @org.springframework.beans.factory.annotation.Autowired private SessionRevocation revocation;
  public TeamService(UserRepository u, SessionRepository s, AuthTokenService t, MailService m, JdbcTemplate j, OrganizationRepository o, @Value("${fleetflow.app-url}") String appUrl) { users = u; sessions = s; tokens = t; mail = m; jdbc = j; orgs = o; this.appUrl = appUrl; }
  private static ApiException forbidden(String m) { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", m); }
  private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Member not found"); }
  private static void assertCanGrant(AuthPrincipal a, Role r) {
    if (r == Role.OWNER) throw forbidden("Ownership cannot be granted");
    if (a.role() == Role.ADMIN && r == Role.ADMIN) throw forbidden("Only the owner can grant the admin role");
  }
  private AppUser target(AuthPrincipal a, UUID id) {
    var t = users.findByIdAndOrganizationId(id, a.organizationId()).orElseThrow(TeamService::notFound); // other tenants' ids are simply "not found"
    if (t.getId().equals(a.userId())) throw forbidden("You cannot change your own account here");
    if (t.getRole() == Role.OWNER) throw forbidden("The owner cannot be modified");
    if (a.role() == Role.ADMIN && t.getRole() == Role.ADMIN) throw forbidden("Only the owner can manage admins");
    return t;
  }
  @Transactional(readOnly = true) public List<Member> members() {
    return users.findByOrganizationIdOrderByCreatedAtAsc(TenantContext.organizationId()).stream().map(u -> new Member(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isEnabled(), u.isEmailVerified())).toList();
  }
  @Transactional(readOnly = true) public List<Invitation> invitations() {
    return jdbc.query("SELECT id, email, role, expires_at FROM auth_tokens WHERE organization_id = ? AND type = 'INVITATION' AND used_at IS NULL AND expires_at > now() ORDER BY created_at DESC",
        (rs, i) -> new Invitation(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("role"), rs.getTimestamp("expires_at").toInstant()), TenantContext.organizationId());
  }
  @Transactional public void invite(String email, Role role, UUID driverId) {
    var a = TenantContext.current(); assertCanGrant(a, role);
    if (driverId != null) {
      if (role != Role.DRIVER) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "driverId is only valid for the DRIVER role");
      var free = jdbc.queryForList("SELECT 1 FROM drivers WHERE id = ? AND organization_id = ? AND user_id IS NULL", driverId, a.organizationId());
      if (free.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "DRIVER_UNAVAILABLE_FOR_LINK", "Driver not found or already has a login");
    }
    if (users.existsByEmailIgnoreCase(email)) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "That email already has an account");
    String raw = tokens.create("INVITATION", a.organizationId(), null, email.toLowerCase(), role.name(), Duration.ofDays(7), driverId);
    String org = orgs.findById(a.organizationId()).map(Organization::getName).orElse("your team");
    mail.sendAfterCommit(email, "You're invited to " + org + " on FleetFlow", "Join as " + role + ": " + appUrl + "/accept-invitation?token=" + raw);
  }
  @Transactional public Member changeRole(UUID id, Role role) {
    var a = TenantContext.current(); assertCanGrant(a, role); var t = target(a, id);
    t.setRole(role); users.save(t); revocation.revokeUser(t.getId()); // force re-login so the new role is in their next token
    return new Member(t.getId(), t.getName(), t.getEmail(), t.getRole(), t.isEnabled(), t.isEmailVerified());
  }
  @Transactional public Member setEnabled(UUID id, boolean enabled) {
    var t = target(TenantContext.current(), id);
    t.setEnabled(enabled); users.save(t); if (!enabled) revocation.revokeUser(t.getId());
    return new Member(t.getId(), t.getName(), t.getEmail(), t.getRole(), t.isEnabled(), t.isEmailVerified());
  }
  @Transactional public void remove(UUID id) {
    var t = target(TenantContext.current(), id);
    revocation.revokeUser(t.getId());
    jdbc.update("UPDATE drivers SET user_id = NULL WHERE user_id = ?", t.getId());
    users.delete(t);
  }
}

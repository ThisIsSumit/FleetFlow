package com.fleetflow.auth;
import com.fleetflow.auth.AuthDtos.*;
import com.fleetflow.security.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class AuthController {
  private static final String COOKIE = "ff_refresh";
  private final AuthService auth; private final boolean secure; private final com.fleetflow.account.AccountService account;
  public AuthController(AuthService auth, com.fleetflow.account.AccountService account, @Value("${fleetflow.refresh.cookie-secure}") boolean secure) { this.auth = auth; this.account = account; this.secure = secure; }

  private ResponseEntity<AuthResponse> respond(AuthService.Issued i, HttpStatus st) {
    return ResponseEntity.status(st).header(HttpHeaders.SET_COOKIE, cookie(i.refreshToken(), Duration.ofSeconds(i.refreshTtlSeconds())).toString()).body(i.body());
  }
  private ResponseCookie cookie(String v, Duration age) {
    return ResponseCookie.from(COOKIE, v).httpOnly(true).secure(secure).sameSite("Strict").path("/api/v1/auth").maxAge(age).build();
  }
  private static String refreshCookie(HttpServletRequest r) {
    if (r.getCookies() == null) return null;
    return Arrays.stream(r.getCookies()).filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
  }

  @PostMapping("/auth/signup") ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest b, HttpServletRequest r) {
    var issued = auth.signup(b, r.getHeader("User-Agent"), r.getRemoteAddr());
    try { account.sendVerification(issued.body().user().id()); } catch (Exception ignored) { /* signup must not fail because email is down; user can resend */ }
    return respond(issued, HttpStatus.CREATED);
  }
  @PostMapping("/auth/accept-invitation") ResponseEntity<AuthResponse> accept(@Valid @RequestBody com.fleetflow.account.AccountController.AcceptBody b, HttpServletRequest r) {
    return respond(account.acceptInvitation(b.token(), b.name(), b.password(), r.getHeader("User-Agent"), r.getRemoteAddr()), HttpStatus.CREATED);
  }
  @PostMapping("/auth/login") ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest b, HttpServletRequest r) {
    return respond(auth.login(b, r.getHeader("User-Agent"), r.getRemoteAddr()), HttpStatus.OK);
  }
  @PostMapping("/auth/refresh") ResponseEntity<AuthResponse> refresh(HttpServletRequest r) { return respond(auth.refresh(refreshCookie(r)), HttpStatus.OK); }
  @PostMapping("/auth/logout") ResponseEntity<Void> logout(HttpServletRequest r) {
    auth.logout(refreshCookie(r));
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
  }
  @PostMapping("/auth/logout-all") ResponseEntity<Void> logoutAll() {
    auth.logoutAll(TenantContext.current().userId());
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
  }
  @GetMapping("/auth/sessions") List<SessionDto> sessions() { var p = TenantContext.current(); return auth.sessions(p.userId(), p.sessionId()); }
  @DeleteMapping("/auth/sessions/{id}") ResponseEntity<Void> revoke(@PathVariable UUID id) {
    auth.revoke(TenantContext.current().userId(), id); return ResponseEntity.noContent().build();
  }
  @GetMapping("/me") UserDto me() { var p = TenantContext.current(); return auth.me(p.userId(), p.organizationId()); }
}

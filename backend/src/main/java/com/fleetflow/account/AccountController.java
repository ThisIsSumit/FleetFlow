package com.fleetflow.account;
import com.fleetflow.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth")
public class AccountController {
  public record TokenBody(@NotBlank String token) {}
  public record EmailBody(@NotBlank @Email String email) {}
  public record ResetBody(@NotBlank String token, @NotBlank @Size(min = 10, max = 72) String newPassword) {}
  public record AcceptBody(@NotBlank String token, @NotBlank @Size(max = 120) String name, @NotBlank @Size(min = 10, max = 72) String password) {}
  private final AccountService svc; public AccountController(AccountService svc) { this.svc = svc; }
  @PostMapping("/forgot-password") public ResponseEntity<Void> forgot(@Valid @RequestBody EmailBody b) { svc.forgotPassword(b.email()); return ResponseEntity.noContent().build(); }
  @PostMapping("/reset-password") public ResponseEntity<Void> reset(@Valid @RequestBody ResetBody b) { svc.resetPassword(b.token(), b.newPassword()); return ResponseEntity.noContent().build(); }
  @PostMapping("/verify-email") public ResponseEntity<Void> verify(@Valid @RequestBody TokenBody b) { svc.verifyEmail(b.token()); return ResponseEntity.noContent().build(); }
  @PostMapping("/resend-verification") public ResponseEntity<Void> resend() { svc.sendVerification(TenantContext.current().userId()); return ResponseEntity.noContent().build(); }
}

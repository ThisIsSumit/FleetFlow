package com.fleetflow.auth;
import com.fleetflow.account.TeamService;
import com.fleetflow.account.TeamService.*;
import com.fleetflow.domain.*;
import com.fleetflow.domain.Repos.OrganizationRepository;
import com.fleetflow.common.ApiException;
import com.fleetflow.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class TeamController {
  public record InviteBody(@NotBlank @Email String email, @NotNull Role role, UUID driverId) {}
  public record RoleBody(@NotNull Role role) {}
  public record EnabledBody(boolean enabled) {}
  public record OrgBody(@NotBlank @Size(max = 160) String name) {}
  public record OrgDto(UUID id, String name) {}
  static final String STAFF_MGR = "hasAnyRole('OWNER','ADMIN')";
  private final TeamService team; private final OrganizationRepository orgs;
  public TeamController(TeamService team, OrganizationRepository orgs) { this.team = team; this.orgs = orgs; }
  @GetMapping("/team") @PreAuthorize(STAFF_MGR) public List<Member> list() { return team.members(); }
  @GetMapping("/team/invitations") @PreAuthorize(STAFF_MGR) public List<Invitation> invitations() { return team.invitations(); }
  @PostMapping("/team/invitations") @PreAuthorize(STAFF_MGR) public ResponseEntity<Void> invite(@Valid @RequestBody InviteBody b) { team.invite(b.email(), b.role(), b.driverId()); return ResponseEntity.status(HttpStatus.CREATED).build(); }
  @PatchMapping("/team/{id}/role") @PreAuthorize(STAFF_MGR) public Member role(@PathVariable UUID id, @Valid @RequestBody RoleBody b) { return team.changeRole(id, b.role()); }
  @PatchMapping("/team/{id}/enabled") @PreAuthorize(STAFF_MGR) public Member enabled(@PathVariable UUID id, @RequestBody EnabledBody b) { return team.setEnabled(id, b.enabled()); }
  @DeleteMapping("/team/{id}") @PreAuthorize(STAFF_MGR) public ResponseEntity<Void> remove(@PathVariable UUID id) { team.remove(id); return ResponseEntity.noContent().build(); }
  @GetMapping("/organization") public OrgDto org() { var id = TenantContext.organizationId(); return new OrgDto(id, orgs.findById(id).map(Organization::getName).orElse("")); }
  @PutMapping("/organization") @PreAuthorize("hasRole('OWNER')")
  public OrgDto rename(@Valid @RequestBody OrgBody b) {
    var o = orgs.findById(TenantContext.organizationId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Organization not found"));
    o.rename(b.name().trim()); orgs.save(o); return new OrgDto(o.getId(), o.getName());
  }
}

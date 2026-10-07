package com.fleetflow.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "users")
public class AppUser {
  @Id private UUID id = UUID.randomUUID();
  @Column(name = "organization_id") private UUID organizationId;
  private String name;
  private String email;
  @Column(name = "password_hash") private String passwordHash;
  @Enumerated(EnumType.STRING) private Role role;
  private boolean enabled = true;
  @Column(name = "email_verified") private boolean emailVerified;
  @Column(name = "created_at") private Instant createdAt = Instant.now();
  protected AppUser() {}
  public AppUser(UUID orgId, String name, String email, String hash, Role role) {
    this.organizationId = orgId; this.name = name; this.email = email.toLowerCase(); this.passwordHash = hash; this.role = role;
  }
  public void setPasswordHash(String h) { this.passwordHash = h; }
  public void setEmailVerified(boolean v) { this.emailVerified = v; }
  public void setRole(Role r) { this.role = r; }
  public void setEnabled(boolean e) { this.enabled = e; }
  public UUID getId() { return id; }
  public UUID getOrganizationId() { return organizationId; }
  public String getName() { return name; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public Role getRole() { return role; }
  public boolean isEnabled() { return enabled; }
  public boolean isEmailVerified() { return emailVerified; }
}

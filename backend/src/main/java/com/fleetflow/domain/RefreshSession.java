package com.fleetflow.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "refresh_sessions")
public class RefreshSession {
  @Id private UUID id = UUID.randomUUID();
  @Column(name = "user_id") private UUID userId;
  @Column(name = "organization_id") private UUID organizationId;
  @Column(name = "token_hash") private String tokenHash;
  @Column(name = "previous_token_hash") private String previousTokenHash;
  @Column(name = "user_agent") private String userAgent;
  private String ip;
  @Column(name = "created_at") private Instant createdAt = Instant.now();
  @Column(name = "last_used_at") private Instant lastUsedAt = Instant.now();
  @Column(name = "expires_at") private Instant expiresAt;
  protected RefreshSession() {}
  public RefreshSession(UUID userId, UUID orgId, String hash, String ua, String ip, Instant exp) {
    this.userId = userId; this.organizationId = orgId; this.tokenHash = hash; this.userAgent = ua; this.ip = ip; this.expiresAt = exp;
  }
  public void rotate(String newHash, Instant newExpiry) {
    this.previousTokenHash = this.tokenHash; this.tokenHash = newHash; this.lastUsedAt = Instant.now(); this.expiresAt = newExpiry;
  }
  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public UUID getOrganizationId() { return organizationId; }
  public String getUserAgent() { return userAgent; }
  public String getIp() { return ip; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getLastUsedAt() { return lastUsedAt; }
  public Instant getExpiresAt() { return expiresAt; }
}

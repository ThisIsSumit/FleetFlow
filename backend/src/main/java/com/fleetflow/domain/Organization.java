package com.fleetflow.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "organizations")
public class Organization {
  @Id private UUID id = UUID.randomUUID();
  private String name;
  @Column(name = "created_at") private Instant createdAt = Instant.now();
  protected Organization() {}
  public Organization(String name) { this.name = name; }
  public void rename(String n) { this.name = n; }
  public UUID getId() { return id; }
  public String getName() { return name; }
}

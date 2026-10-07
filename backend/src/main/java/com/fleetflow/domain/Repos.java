package com.fleetflow.domain;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
public class Repos {
  public interface OrganizationRepository extends JpaRepository<Organization, UUID> {}
  public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<AppUser> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<AppUser> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
  }
  public interface SessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByTokenHash(String hash);
    Optional<RefreshSession> findByPreviousTokenHash(String hash);
    List<RefreshSession> findByUserIdOrderByLastUsedAtDesc(UUID userId);
    Optional<RefreshSession> findByIdAndUserId(UUID id, UUID userId);
    @Modifying @Transactional long deleteByUserId(UUID userId);
  }
}

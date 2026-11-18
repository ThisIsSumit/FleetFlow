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
    /** Row-locking lookups for refresh: concurrent refreshes of one session serialize instead of overwriting each other. */
    @Query(value = "SELECT * FROM refresh_sessions WHERE token_hash = :h FOR UPDATE", nativeQuery = true)
    Optional<RefreshSession> lockByTokenHash(@org.springframework.data.repository.query.Param("h") String hash);
    @Query(value = "SELECT * FROM refresh_sessions WHERE previous_token_hash = :h FOR UPDATE", nativeQuery = true)
    Optional<RefreshSession> lockByPreviousTokenHash(@org.springframework.data.repository.query.Param("h") String hash);
    List<RefreshSession> findByUserIdOrderByLastUsedAtDesc(UUID userId);
    Optional<RefreshSession> findByIdAndUserId(UUID id, UUID userId);
    @Modifying @Transactional long deleteByUserId(UUID userId);
  }
}

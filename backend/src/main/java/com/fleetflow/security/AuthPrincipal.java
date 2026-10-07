package com.fleetflow.security;
import com.fleetflow.domain.Role;
import java.util.UUID;
/** Identity derived ONLY from the verified JWT. Never from request bodies/params. */
public record AuthPrincipal(UUID userId, UUID organizationId, Role role, UUID sessionId) {}

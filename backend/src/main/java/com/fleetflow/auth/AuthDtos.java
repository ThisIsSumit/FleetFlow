package com.fleetflow.auth;
import com.fleetflow.domain.Role;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public class AuthDtos {
  public record SignupRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 10, max = 72) String password, @NotBlank @Size(max = 160) String organizationName) {}
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record UserDto(UUID id, UUID organizationId, String organizationName, String name, String email, Role role, boolean emailVerified) {}
  public record AuthResponse(String accessToken, long expiresIn, UserDto user) {}
  public record SessionDto(UUID id, String userAgent, String ip, Instant createdAt, Instant lastUsedAt, boolean current) {}
}

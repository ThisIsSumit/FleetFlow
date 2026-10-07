package com.fleetflow.security;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
/** Every tenant-scoped query must take its organizationId from here. */
public final class TenantContext {
  private TenantContext() {}
  public static AuthPrincipal current() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !(a.getPrincipal() instanceof AuthPrincipal p)) throw new IllegalStateException("No authenticated principal");
    return p;
  }
  public static java.util.UUID organizationId() { return current().organizationId(); }
}

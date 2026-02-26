package org.dreamhorizon.pulseserver.tenant;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local context holder for tenant information.
 * This class provides access to the current tenant throughout the request lifecycle.
 *
 * <p>In Vert.x, we use the Vert.x Context to store tenant information, which is
 * automatically propagated across async operations within the same request.</p>
 */
@Slf4j
public final class TenantContext {

  private static final String TENANT_ID_KEY = "pulse.tenant.id";
  private static final String TENANT_KEY = "pulse.tenant";

  /**
   * Fallback ThreadLocal for non-Vert.x contexts (e.g., tests, background jobs).
   */
  private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();
  private static final ThreadLocal<Tenant> TENANT_HOLDER = new ThreadLocal<>();

  private TenantContext() {
    // Utility class, no instantiation
  }

  /**
   * Sets the current tenant ID for the request.
   *
   * @param tenantId the tenant ID to set
   */
  public static void setTenantId(String tenantId) {
    Context context = Vertx.currentContext();
    if (context != null) {
      context.putLocal(TENANT_ID_KEY, tenantId);
    } else {
      TENANT_ID_HOLDER.set(tenantId);
    }
    log.debug("Tenant context set to: {}", tenantId);
  }

  /**
   * Sets the current tenant for the request.
   *
   * @param tenant the tenant to set
   */
  public static void setTenant(Tenant tenant) {
    Context context = Vertx.currentContext();
    if (context != null) {
      context.putLocal(TENANT_KEY, tenant);
      if (tenant != null) {
        context.putLocal(TENANT_ID_KEY, tenant.getTenantId());
      }
    } else {
      TENANT_HOLDER.set(tenant);
      if (tenant != null) {
        TENANT_ID_HOLDER.set(tenant.getTenantId());
      }
    }
    log.debug("Tenant context set to: {}", tenant != null ? tenant.getTenantId() : null);
  }

  /**
   * Gets the current tenant ID.
   *
   * @return the current tenant ID, or the default tenant ID if not set
   */
  public static String getTenantId() {
    Context context = Vertx.currentContext();
    String tenantId;
    if (context != null) {
      tenantId = context.getLocal(TENANT_ID_KEY);
    } else {
      tenantId = TENANT_ID_HOLDER.get();
    }
    return tenantId;
  }

  /**
   * Gets the current tenant ID as an Optional.
   *
   * @return Optional containing the tenant ID, or empty if not set
   */
  public static Optional<String> getCurrentTenantId() {
    Context context = Vertx.currentContext();
    String tenantId;
    if (context != null) {
      tenantId = context.getLocal(TENANT_ID_KEY);
    } else {
      tenantId = TENANT_ID_HOLDER.get();
    }
    return Optional.ofNullable(tenantId);
  }

  /**
   * Gets the current tenant.
   *
   * @return Optional containing the tenant, or empty if not set
   */
  public static Optional<Tenant> getCurrentTenant() {
    Context context = Vertx.currentContext();
    Tenant tenant;
    if (context != null) {
      tenant = context.getLocal(TENANT_KEY);
    } else {
      tenant = TENANT_HOLDER.get();
    }
    return Optional.ofNullable(tenant);
  }


  /**
   * Clears the tenant context.
   * Should be called at the end of request processing.
   */
  public static void clear() {
    Context context = Vertx.currentContext();
    if (context != null) {
      context.removeLocal(TENANT_ID_KEY);
      context.removeLocal(TENANT_KEY);
    } else {
      TENANT_ID_HOLDER.remove();
      TENANT_HOLDER.remove();
    }
    log.debug("Tenant context cleared");
  }

  /**
   * Checks if a tenant context is currently set.
   *
   * @return true if a tenant context is set, false otherwise
   */
  public static boolean isSet() {
    return getCurrentTenantId().isPresent();
  }

  /**
   * Requires a tenant context to be set, throwing an exception if not.
   *
   * @return the current tenant ID
   * @throws IllegalStateException if no tenant context is set
   */
  public static String requireTenantId() {
    return getCurrentTenantId()
        .orElseThrow(() -> new IllegalStateException("No tenant context is set"));
  }
}


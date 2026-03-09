package org.dreamhorizon.pulseserver.tenant;

import io.jsonwebtoken.Claims;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.JwtService;

/**
 * JAX-RS filter that extracts tenant information from the request and sets it in the TenantContext.
 *
 * <p>Tenant resolution order:</p>
 * <ol>
 *   <li>JWT token tenantId claim from Authorization header</li>
 *   <li>X-Tenant-ID header (explicit override, useful for admin operations)</li>
 *   <li>X-API-KEY header (API key for authentication)</li>
 * </ol>
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHENTICATION + 10) // Run after authentication but before authorization
public class TenantFilter implements ContainerRequestFilter, ContainerResponseFilter {

  public static final String TENANT_HEADER = "X-Tenant-ID";
  public static final String API_KEY_HEADER = "X-API-KEY";
  public static final String PROJECT_HEADER = "X-Project-ID";
  private static final String HEALTHCHECK_PATH = "healthcheck";
  private static final String AUTH_PATH_PREFIX = "v1/auth";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String CLAIM_TENANT_ID = "tenantId";
  private static final String ALERTS_PATH_PREFIX = "alerts";
  private static final String LOGS_INGESTION_PATH = "v1/logs";

  private JwtService jwtService;

  /**
   * Sets the JwtService for testing purposes.
   *
   * @param jwtService the JwtService to use
   */
  void setJwtService(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();

    // Skip tenant resolution for excluded paths
    if (isExcludedPath(path)) {
      log.debug("Skipping tenant resolution for excluded path: {}", path);
      return;
    }

    String tenantId = resolveTenantId(requestContext);
    TenantContext.setTenantId(tenantId);
    log.debug("Request tenant context set to: {} for path: {}",
        tenantId, path);
  }

  private boolean isExcludedPath(String path) {
    if (path == null) {
      return false;
    }
    // Normalize path by removing leading slash
    String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
    return normalizedPath.equals(HEALTHCHECK_PATH)
        || normalizedPath.startsWith(HEALTHCHECK_PATH + "/")
        || normalizedPath.startsWith(AUTH_PATH_PREFIX)
        || normalizedPath.startsWith(ALERTS_PATH_PREFIX)
        || normalizedPath.startsWith(LOGS_INGESTION_PATH);
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    // Clear tenant context after request processing
    TenantContext.clear();
  }

  /**
   * Resolves the tenant ID from the request.
   *
   * @param requestContext the request context
   * @return the resolved tenant ID, or default if header not present
   */
  private String resolveTenantId(ContainerRequestContext requestContext) {
    // Priority 1: Extract tenantId from JWT token in Authorization header
    String tokenTenantId = extractTenantIdFromToken(requestContext);
    if (tokenTenantId != null && !tokenTenantId.isBlank()) {
      log.debug("Tenant ID resolved from JWT token: {}", tokenTenantId);
      return tokenTenantId.trim();
    }

    // Priority 2: X-API-KEY header - extract project ID from API key
    String apiKey = requestContext.getHeaderString(API_KEY_HEADER);
    if (apiKey != null && !apiKey.isBlank()) {
      try {
        String projectId = extractProjectIdFromApiKey(apiKey.trim());
        log.debug("Project ID extracted from API key header: {} (from: {})", projectId, apiKey);
        return projectId;
      } catch (IllegalArgumentException e) {
        log.error("Invalid API key format: {}. Error: {}", apiKey, e.getMessage());
        requestContext.abortWith(
            jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"Invalid API key format.\"}")
                .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .build());
        return null;
      }
    }

    // Priority 3: Explicit X-Tenant-ID header (fallback)
    //Todo: This will be removed once we have the complete project Onboarding in place
    String headerTenantId = requestContext.getHeaderString(TENANT_HEADER);
    if (headerTenantId != null && !headerTenantId.isBlank()) {
      log.debug("Tenant ID resolved from header: {}", headerTenantId);
      return headerTenantId.trim();
    }

    //This a Temporary fix for supporting the projectId.
    //TODO: This will be replaced once we have the complete project Onboarding in place
    String projectId = requestContext.getHeaderString(PROJECT_HEADER);
    if (projectId != null && !projectId.isBlank()) {
      return projectId.trim();
    }

    log.error("Missing tenant ID (not found in token or X-API-KEY header or X-Tenant-ID header) for path: {}",
        requestContext.getUriInfo().getPath());
    requestContext.abortWith(
        jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.BAD_REQUEST)
            .entity("{\"error\": \"Tenant ID is required (via Authorization token or X-API-KEY header or X-Tenant-ID header)\"}")
            .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
            .build());
    return null;
  }

  /**
   * Extracts the tenantId claim from the JWT token in the Authorization header.
   *
   * @param requestContext the request context
   * @return the tenantId from the token, or null if not found or invalid
   */
  private String extractTenantIdFromToken(ContainerRequestContext requestContext) {
    String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }

    String token = authHeader.substring(BEARER_PREFIX.length()).trim();
    if (token.isBlank()) {
      return null;
    }

    try {
      JwtService service = getJwtService();
      if (service == null) {
        log.warn("JwtService not available, skipping token-based tenant resolution");
        return null;
      }

      Claims claims = service.verifyToken(token);
      String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
      return tenantId;
    } catch (Exception e) {
      log.debug("Failed to extract tenantId from token: {}", e.getMessage());
      return null;
    }
  }

  private JwtService getJwtService() {
    if (jwtService == null) {
      jwtService = GuiceInjector.getGuiceInjector().getInstance(JwtService.class);
    }
    return jwtService;
  }

  private String extractProjectIdFromApiKey(String apiKey) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key cannot be null or blank");
    }
    
    int lastUnderscoreIndex = apiKey.lastIndexOf('_');
    if (lastUnderscoreIndex == -1) {
      throw new IllegalArgumentException("Invalid API key format.");
    }
    
    // Extract everything before the last underscore
    return apiKey.substring(0, lastUnderscoreIndex);
  }
}


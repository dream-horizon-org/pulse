package org.dreamhorizon.pulseserver.filter;

import io.jsonwebtoken.Claims;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;

/**
 * JAX-RS authorization filter that checks user permissions using OpenFGA.
 * This filter runs after authentication and tenant/project context extraction.
 * <p>
 * Permission checks are performed for project-scoped resources based on the HTTP method:
 * - GET/HEAD → "view" permission
 * - POST/PUT/PATCH → "edit" permission
 * - DELETE → "delete" permission
 * <p>
 * Note: Uses lazy initialization for dependencies since JAX-RS @Provider classes
 * are instantiated by the JAX-RS container, not by Guice.
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFilter implements ContainerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String HEALTHCHECK_PATH = "healthcheck";
  private static final String AUTH_PATH_PREFIX = "v1/auth";
  private static final String ONBOARDING_PATH_PREFIX = "v1/onboarding";
  private static final String TNC_DOCUMENTS_PATH = "v1/tnc/documents";
  private static final String CONFIG_PATH = "v1/configs";
  private static final String ALERTS_PATH_PREFIX = "alerts";
  private static final String SYMBOL_UPLOAD_PREFIX = "v1/symbolicate/file/upload";

  @Context
  private ResourceInfo resourceInfo;

  private OpenFgaService openFgaService;
  private JwtService jwtService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();

    if (isExcludedPath(path)) {
      log.debug("Skipping authorization for excluded path: {}", path);
      return;
    }

    RequiresPermission permission = getRequiresPermission();
    if (permission == null) {
      log.debug("No @RequiresPermission annotation for path: {}, skipping authorization", path);
      return;
    }

    String projectId = ProjectContext.requireProjectId();

    String userId = extractUserIdFromToken(requestContext);
    if (userId == null) {
      log.warn("Authorization check failed: missing user ID in token for path: {}", path);
      abortUnauthorized(requestContext, "Missing or invalid authentication token");
      return;
    }

    String action = permission.value();

    log.debug("Checking permission: userId={}, action={}, projectId={}", userId, action, projectId);

    try {
      Boolean hasPermission = getOpenFgaService().checkPermission(userId, action, "project", projectId)
          .blockingGet();

      if (!hasPermission) {
        log.warn("Access denied: userId={}, action={}, projectId={}, path={}",
            userId, action, projectId, path);
        abortForbidden(requestContext,
            "Access denied: You don't have " + action + " permission for this project");
        return;
      }

      log.debug("Permission granted: userId={}, action={}, projectId={}", userId, action, projectId);

    } catch (Exception e) {
      log.error("Permission check failed: userId={}, projectId={}, error={}",
          userId, projectId, e.getMessage(), e);
      abortInternalError(requestContext, "Authorization check failed");
    }
  }

  /**
   * Check if the path should skip authorization checks.
   */
  private boolean isExcludedPath(String path) {
    if (path == null) {
      return false;
    }

    String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

    return normalizedPath.equals(HEALTHCHECK_PATH)
        || normalizedPath.startsWith(HEALTHCHECK_PATH + "/")
        || normalizedPath.startsWith(AUTH_PATH_PREFIX)
        || normalizedPath.startsWith(ONBOARDING_PATH_PREFIX)
        || normalizedPath.startsWith(TNC_DOCUMENTS_PATH)
        || normalizedPath.startsWith(CONFIG_PATH)
        || normalizedPath.startsWith(ALERTS_PATH_PREFIX)
        || normalizedPath.startsWith(SYMBOL_UPLOAD_PREFIX);
  }

  /**
   * Returns the {@link RequiresPermission} annotation from the resource method
   * or its class, or {@code null} if neither is annotated.
   */
  private RequiresPermission getRequiresPermission() {
    if (resourceInfo == null) {
      return null;
    }
    Method resourceMethod = resourceInfo.getResourceMethod();
    if (resourceMethod != null) {
      RequiresPermission methodAnnotation = resourceMethod.getAnnotation(RequiresPermission.class);
      if (methodAnnotation != null) {
        return methodAnnotation;
      }
    }
    Class<?> resourceClass = resourceInfo.getResourceClass();
    if (resourceClass != null) {
      return resourceClass.getAnnotation(RequiresPermission.class);
    }
    return null;
  }

  /**
   * Extract user ID from JWT token in Authorization header.
   */
  private String extractUserIdFromToken(ContainerRequestContext requestContext) {
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
        log.warn("JwtService not available");
        return null;
      }

      Claims claims = service.verifyToken(token);
      return claims.getSubject();  // User ID is in the subject claim

    } catch (Exception e) {
      log.debug("Failed to extract user ID from token: {}", e.getMessage());
      return null;
    }
  }

  private JwtService getJwtService() {
    if (jwtService == null) {
      jwtService = GuiceInjector.getGuiceInjector().getInstance(JwtService.class);
    }
    return jwtService;
  }

  private OpenFgaService getOpenFgaService() {
    if (openFgaService == null) {
      openFgaService = GuiceInjector.getGuiceInjector().getInstance(OpenFgaService.class);
    }
    return openFgaService;
  }

  private void abortUnauthorized(ContainerRequestContext requestContext, String message) {
    requestContext.abortWith(
        Response.status(Response.Status.UNAUTHORIZED)
            .entity("{\"error\": \"Unauthorized\", \"message\": \"" + message + "\"}")
            .type("application/json")
            .build()
    );
  }

  private void abortForbidden(ContainerRequestContext requestContext, String message) {
    requestContext.abortWith(
        Response.status(Response.Status.FORBIDDEN)
            .entity("{\"error\": \"Forbidden\", \"message\": \"" + message + "\"}")
            .type("application/json")
            .build()
    );
  }

  private void abortInternalError(ContainerRequestContext requestContext, String message) {
    requestContext.abortWith(
        Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("{\"error\": \"Internal Server Error\", \"message\": \"" + message + "\"}")
            .type("application/json")
            .build()
    );
  }
}

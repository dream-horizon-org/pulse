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

  @Context
  private ResourceInfo resourceInfo;

  private OpenFgaService openFgaService;
  private JwtService jwtService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();

    // Skip authorization for excluded paths
    if (isExcludedPath(path)) {
      log.debug("Skipping authorization for excluded path: {}", path);
      return;
    }

    // Check if project context is set (required for project-scoped resources)
    String projectId = ProjectContext.getProjectId();
    if (projectId == null || projectId.isBlank()) {
      // No project context - this might be a tenant-level resource
      log.debug("No project context for path: {}", path);
      return;
    }

    // Extract user ID from JWT token
    String userId = extractUserIdFromToken(requestContext);
    if (userId == null) {
      log.warn("Authorization check failed: missing user ID in token for path: {}", path);
      abortUnauthorized(requestContext, "Missing or invalid authentication token");
      return;
    }

    String action = resolveAction(requestContext.getMethod());

    log.debug("Checking permission: userId={}, action={}, projectId={}", userId, action, projectId);

    // Perform permission check
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
        || normalizedPath.startsWith(CONFIG_PATH);
  }

  /**
   * Resolve the required OpenFGA permission for the current request.
   * Checks for a {@link RequiresPermission} annotation on the resource method
   * (or its class) first; falls back to HTTP-method-based inference.
   */
  private String resolveAction(String httpMethod) {
    if (resourceInfo != null) {
      Method resourceMethod = resourceInfo.getResourceMethod();
      if (resourceMethod != null) {
        RequiresPermission methodAnnotation = resourceMethod.getAnnotation(RequiresPermission.class);
        if (methodAnnotation != null) {
          return methodAnnotation.value();
        }
      }
      Class<?> resourceClass = resourceInfo.getResourceClass();
      if (resourceClass != null) {
        RequiresPermission classAnnotation = resourceClass.getAnnotation(RequiresPermission.class);
        if (classAnnotation != null) {
          return classAnnotation.value();
        }
      }
    }
    return mapHttpMethodToAction(httpMethod);
  }

  private String mapHttpMethodToAction(String method) {
    return switch (method.toUpperCase()) {
      case "GET", "HEAD" -> "can_view";
      case "POST", "PUT", "PATCH" -> "can_edit";
      case "DELETE" -> "can_delete_project";
      default -> "can_view";
    };
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

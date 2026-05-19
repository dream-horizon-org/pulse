package org.dreamhorizon.pulseserver.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.guice.GuiceInjector;
import org.dreamhorizon.pulseserver.service.JwtService;

/**
 * JAX-RS filter that authenticates service-to-service calls for the specific internal endpoints
 * used exclusively by pulse-alerts-cron using an opaque bearer token checked against the
 * configured {@code internalServiceTokens} list.
 *
 * <p>Only the 6 cron-only paths listed in {@link #CRON_PATHS} are subject to this filter.
 * All other {@code /internal/*} paths are passed through so that TenantFilter and
 * AuthorizationFilter can handle them via normal user-JWT + OpenFGA superadmin checks.
 *
 * <p>Filter priority is {@link Priorities#AUTHENTICATION} so it runs before
 * {@link AuthorizationFilter} ({@link Priorities#AUTHORIZATION}). When the token is valid it sets
 * the request property {@link #PROP_INTERNAL_AUTHENTICATED} to {@code true}; downstream filters
 * (TenantFilter, AuthorizationFilter) must short-circuit on that property to avoid attempting a
 * user-JWT parse on the opaque service token.
 *
 * <p>In dev mode ({@code googleOauthEnabled=false}) token validation is bypassed so local
 * development and CI work without configuring a service token.
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHENTICATION)
public class InternalServiceAuthFilter implements ContainerRequestFilter {

  public static final String PROP_INTERNAL_AUTHENTICATED = "pulse.internal.authenticated";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final Set<String> CRON_PATHS = Set.of(
      "internal/v1/api-keys/sync-to-redis",
      "internal/v1/projects/limits/sync-to-redis",
      "internal/v1/projects/limits/process-usage-notifications",
      "internal/analytics/funnels",
      "internal/analytics/journeys",
      "internal/analytics/events"
  );

  private ApplicationConfig applicationConfig;
  private JwtService jwtService;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    String path = requestContext.getUriInfo().getPath();
    String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

    if (!CRON_PATHS.contains(normalizedPath)) {
      return;
    }

    ApplicationConfig config = getApplicationConfig();

    if (config != null && !Boolean.TRUE.equals(config.getGoogleOAuthEnabled())) {
      requestContext.setProperty(PROP_INTERNAL_AUTHENTICATED, true);
      return;
    }

    String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      log.warn("InternalServiceAuthFilter: missing or malformed Authorization header for path={}", path);
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
      return;
    }

    String token = authHeader.substring(BEARER_PREFIX.length());
    List<String> allowedTokens = config != null ? config.getInternalServiceTokenList() : List.of();

    boolean isServiceToken = allowedTokens.stream()
        .anyMatch(t -> MessageDigest.isEqual(
            t.getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8)));

    if (isServiceToken) {
      requestContext.setProperty(PROP_INTERNAL_AUTHENTICATED, true);
      return;
    }

    try {
      getJwtService().verifyToken(token);
      log.debug("InternalServiceAuthFilter: valid user JWT for cron path={}, delegating to AuthorizationFilter", path);
      return;
    } catch (Exception e) {
      log.warn("InternalServiceAuthFilter: rejected token that is neither a service token nor a valid JWT for path={}", path);
      requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
    }
  }

  private ApplicationConfig getApplicationConfig() {
    if (applicationConfig == null) {
      applicationConfig = GuiceInjector.getGuiceInjector().getInstance(ApplicationConfig.class);
    }
    return applicationConfig;
  }

  private JwtService getJwtService() {
    if (jwtService == null) {
      jwtService = GuiceInjector.getGuiceInjector().getInstance(JwtService.class);
    }
    return jwtService;
  }

  void setJwtService(JwtService jwtServiceInstance) {
    this.jwtService = jwtServiceInstance;
  }
}

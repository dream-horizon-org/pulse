package org.dreamhorizon.pulseserver.resources.v1.users;

import com.google.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.users.models.UserProjectsResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.UserProjectsService;
import io.jsonwebtoken.Claims;

/**
 * JAX-RS resource for user operations.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/users")
public class UserResource {
    
    private final UserProjectsService userProjectsService;
    private final JwtService jwtService;
    
    /**
     * Get all projects accessible to the current user.
     * Requires Authorization header with JWT token.
     * 
     * @param authorization Authorization header
     * @return UserProjectsResponse with projects and redirect hint
     */
    @GET
    @Path("/me/projects")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response<UserProjectsResponse>> getUserProjects(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        
        try {
            // Extract user info from JWT token
            String token = extractToken(authorization);
            if (token == null) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Unauthorized", "Missing or invalid Authorization header");
            }
            
            Claims claims;
            try {
                claims = jwtService.verifyToken(token);
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                log.warn("Expired JWT token for /users/me/projects");
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Token expired", "Your session has expired. Please log in again.");
            } catch (io.jsonwebtoken.security.SignatureException e) {
                log.warn("Invalid JWT signature for /users/me/projects");
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", "Authentication failed. Please log in again.");
            } catch (io.jsonwebtoken.MalformedJwtException e) {
                log.warn("Malformed JWT token for /users/me/projects");
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", "Malformed authentication token. Please log in again.");
            } catch (Exception e) {
                log.error("JWT verification failed: {}", e.getMessage(), e);
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Authentication failed", "Unable to verify authentication token");
            }
            
            String userId = claims.getSubject();
            String tenantId = claims.get("tenantId", String.class);
            
            if (userId == null || userId.isBlank()) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", "Token missing user ID");
            }
            
            if (tenantId == null || tenantId.isBlank()) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", "Token missing tenant ID");
            }
            
            log.info("Fetching projects for user: userId={}, tenantId={}", userId, tenantId);
            
            return userProjectsService.getUserProjects(userId, tenantId)
                .map(result -> UserProjectsResponse.builder()
                    .tenantId(result.getTenantId())
                    .tenantName(result.getTenantName())
                    .projects(result.getProjects().stream()
                        .map(p -> UserProjectsResponse.ProjectSummary.builder()
                            .projectId(p.getProjectId())
                            .name(p.getName())
                            .description(p.getDescription())
                            .isActive(p.getIsActive())
                            .role(p.getRole())
                            .build())
                        .collect(Collectors.toList()))
                    .redirectTo(result.getRedirectTo())
                    .build())
                .to(RestResponse.jaxrsRestHandler());
                
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error fetching user projects: {}", e.getMessage(), e);
            String cause = e.getMessage() != null ? e.getMessage() : "An unexpected error occurred";
            throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                .getCustomException("Failed to fetch projects", cause);
        }
    }
    
    private String extractToken(String authorization) {
        if (authorization == null || authorization.trim().isEmpty()) {
            return null;
        }
        if (authorization.toLowerCase().startsWith("bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}

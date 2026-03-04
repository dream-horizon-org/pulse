package org.dreamhorizon.pulseserver.resources.v1.onboarding;

import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.onboarding.models.OnboardingRequest;
import org.dreamhorizon.pulseserver.resources.v1.onboarding.models.OnboardingResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OnboardingService;
import io.jsonwebtoken.Claims;

/**
 * JAX-RS resource for onboarding new users.
 * Handles creation of organization and first project for new users.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/onboarding")
public class OnboardingResource {
    
    private final OnboardingService onboardingService;
    private final JwtService jwtService;
    
    /**
     * Complete onboarding for a new user.
     * Creates organization (tenant) and first project.
     * Requires a temporary JWT token from login (without tenant).
     * 
     * @param authorization Authorization header with temporary JWT
     * @param request Onboarding details
     * @return OnboardingResponse with tokens and project details
     */
    @POST
    @Path("/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response<OnboardingResponse>> completeOnboarding(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody(description = "Onboarding details")
            @Valid
            OnboardingRequest request) {
        
        try {
            // Extract user info from JWT token (temporary token from login) or Firebase token
            String token = extractToken(authorization);
            if (token == null) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Missing authorization", "Authorization header required");
            }
            
            String userId;
            String email;
            String name;
            
            // Check if it's a mock/development token (Firebase format)
            if (isMockToken(token)) {
                log.info("Using mock token for onboarding");
                // Parse mock token to determine user
                if (token.contains("user2") || token.contains("2")) {
                    userId = "mock-user-2";
                    email = "user2@example.com";
                    name = "Test User 2";
                } else {
                    userId = "mock-user-1";
                    email = "user1@example.com";
                    name = "Test User 1";
                }
            } else {
                // Try to parse as JWT (our backend token)
                Claims claims = jwtService.verifyToken(token);
                userId = claims.getSubject();
                email = claims.get("email", String.class);
                name = claims.get("name", String.class);
            }
            
            if (userId == null || userId.isBlank()) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", "Token missing user ID");
            }
            
            log.info("Processing onboarding: userId={}, org={}, project={}", 
                userId, request.getOrganizationName(), request.getProjectName());
            
            return onboardingService.completeOnboarding(
                    userId,
                    email,
                    name,
                    request.getOrganizationName(),
                    request.getProjectName(),
                    request.getProjectDescription())
                .map(result -> OnboardingResponse.builder()
                    .userId(result.getUserId())
                    .email(result.getEmail())
                    .name(result.getName())
                    .tenantId(result.getTenantId())
                    .tenantName(result.getTenantName())
                    .tier(result.getTier())
                    .projectId(result.getProjectId())
                    .projectName(result.getProjectName())
                    .projectApiKey(result.getProjectApiKey())
                    .accessToken(result.getAccessToken())
                    .refreshToken(result.getRefreshToken())
                    .tokenType(result.getTokenType())
                    .expiresIn(result.getExpiresIn())
                    .redirectTo(result.getRedirectTo())
                    .build())
                .to(RestResponse.jaxrsRestHandler());
                
        } catch (Exception e) {
            log.error("Onboarding failed: {}", e.getMessage(), e);
            String cause = e.getMessage() != null ? e.getMessage() : "Onboarding failed";
            throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                .getCustomException("Onboarding failed", cause);
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
    
    /**
     * Check if token is a mock/development token.
     */
    private boolean isMockToken(String token) {
        if (token == null) {
            return false;
        }
        return token.startsWith("mock-") 
            || token.startsWith("dev-") 
            || token.equals("test-token-user1")
            || token.equals("test-token-user2")
            || !token.contains(".");
    }
}

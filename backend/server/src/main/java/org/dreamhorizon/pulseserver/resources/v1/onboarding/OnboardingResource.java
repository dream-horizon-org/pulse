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
import org.dreamhorizon.pulseserver.service.AuthService;
import org.dreamhorizon.pulseserver.service.OnboardingService;

/**
 * JAX-RS resource for onboarding new users.
 * Handles creation of organization and first project for new users.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/onboarding")
public class OnboardingResource {
    
    private final OnboardingService onboardingService;
    private final AuthService authService;
    
    /**
     * Complete onboarding for a new user.
     * Creates organization (tenant) and first project.
     * Requires a Firebase ID token.
     * 
     * @param authorization Authorization header with Firebase token
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
            // Extract Firebase token from Authorization header
            String token = extractToken(authorization);
            if (token == null) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Missing authorization", "Authorization header required");
            }
            
            // Check if it's a mock/development token
            if (isMockToken(token)) {
                log.info("Using mock token for onboarding");
                return handleMockOnboarding(token, request);
            }
            
            // Verify Firebase token and complete onboarding
            return authService.verifyFirebaseTokenForOnboarding(token)
                .flatMap(userInfo -> {
                    log.info("Processing onboarding: userId={}, email={}, org={}, project={}", 
                        userInfo.userId, userInfo.email, request.getOrganizationName(), request.getProjectName());
                    
                    return onboardingService.completeOnboarding(
                            userInfo.userId,
                            userInfo.email,
                            userInfo.name,
                            request.getOrganizationName(),
                            request.getProjectName(),
                            request.getProjectDescription());
                })
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
    
    /**
     * Handle mock onboarding for development/testing.
     */
    private CompletionStage<Response<OnboardingResponse>> handleMockOnboarding(
            String token, 
            @Valid OnboardingRequest request) {
        
        // Extract mock user info from token
        String mockUserId = token.replace("mock-", "").replace("dev-", "");
        String mockEmail = mockUserId + "@mock.test";
        String mockName = "Mock User " + mockUserId;
        
        log.info("Processing mock onboarding: userId={}, email={}, org={}, project={}", 
            mockUserId, mockEmail, request.getOrganizationName(), request.getProjectName());
        
        return onboardingService.completeOnboarding(
                mockUserId,
                mockEmail,
                mockName,
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
    }
}

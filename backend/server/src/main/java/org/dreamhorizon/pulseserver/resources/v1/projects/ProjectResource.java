package org.dreamhorizon.pulseserver.resources.v1.projects;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.request.ReqUserInfo;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.projects.models.CreateProjectRequest;
import org.dreamhorizon.pulseserver.resources.v1.projects.models.ProjectResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.ProjectService;

/**
 * REST resource for project management.
 * Handles project creation, updates, and deletion.
 */
@Slf4j
@Path("/v1/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectResource {
    
    private final ProjectService projectService;
    private final JwtService jwtService;
    
    /**
     * Create a new project within the user's tenant.
     * Tenant ID and User ID are extracted from JWT token.
     * 
     * @param authorization JWT token in Authorization header
     * @param request Project creation details
     * @return Created project details
     */
    @POST
    public CompletionStage<Response<ProjectResponse>> createProject(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid CreateProjectRequest request) {
        
        try {
            // Extract tenant ID from context (set by TenantFilter from JWT)
            String tenantId = TenantContext.getTenantId();
            if (tenantId == null || tenantId.isBlank()) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Missing tenant context", "Tenant ID not found in request");
            }
            
            // Extract user info from JWT token
            String token = extractToken(authorization);
            if (token == null) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Missing authorization", "Authorization header required");
            }
            
            ReqUserInfo userInfo;
            try {
                var claims = jwtService.verifyToken(token);
                userInfo = ReqUserInfo.builder()
                    .userId(claims.getSubject())
                    .email(claims.get("email", String.class))
                    .name(claims.get("name", String.class))
                    .build();
            } catch (Exception e) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", e.getMessage());
            }
            
            if (userInfo.getUserId() == null || userInfo.getUserId().isBlank()) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", "User ID not found in token");
            }
            
            log.info("Creating project: name={}, tenantId={}, userId={}", 
                request.getName(), tenantId, userInfo.getUserId());
            
            return projectService.createProject(
                    tenantId,
                    request.getName(),
                    request.getDescription(),
                    userInfo)
                .map(creationResult -> {
                    var project = creationResult.getProject();
                    return ProjectResponse.builder()
                        .projectId(project.getProjectId())
                        .name(project.getName())
                        .description(project.getDescription())
                        .tenantId(project.getTenantId())
                        .apiKey(creationResult.getRawApiKey())
                        .createdAt(project.getCreatedAt())
                        .createdBy(project.getCreatedBy())
                        .build();
                })
                .to(RestResponse.jaxrsRestHandler());
                
        } catch (Exception e) {
            log.error("Failed to create project: {}", e.getMessage(), e);
            throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                .getCustomException("Project creation failed", e.getMessage());
        }
    }
    
    /**
     * Get project details by ID.
     * 
     * @param projectId Project ID
     * @return Project details
     */
    @GET
    @Path("/{projectId}")
    public CompletionStage<Response<ProjectResponse>> getProject(
            @PathParam("projectId") String projectId) {
        
        log.info("Getting project: projectId={}", projectId);
        
        return projectService.getProjectById(projectId)
            .map(project -> ProjectResponse.builder()
                .projectId(project.getProjectId())
                .name(project.getName())
                .description(project.getDescription())
                .tenantId(project.getTenantId())
                .apiKey(null) // API key is only returned at creation time
                .createdAt(project.getCreatedAt())
                .createdBy(project.getCreatedBy())
                .build())
            .to(RestResponse.jaxrsRestHandler());
    }
    
    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.toLowerCase().startsWith("bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}

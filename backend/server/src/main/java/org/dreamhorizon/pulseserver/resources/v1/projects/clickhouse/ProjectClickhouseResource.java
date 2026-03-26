package org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse.models.AuditHistoryResponse;
import org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse.models.AuditLogResponse;
import org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse.models.CredentialsResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.ClickhouseProjectService;
import org.dreamhorizon.pulseserver.service.JwtService;

import java.util.concurrent.CompletionStage;

@Slf4j
@Path("/v1/projects/{projectId}/clickhouse")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectClickhouseResource {
    
    private final ClickhouseProjectService clickhouseProjectService;
    private final JwtService jwtService;
    
    /**
     * Setup ClickHouse credentials for a project.
     * POST /v1/projects/{projectId}/clickhouse/setup
     */
    @POST
    @Path("/setup")
    public CompletionStage<Response<CredentialsResponse>> setupCredentials(
            @PathParam("projectId") String projectId,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        
        String performedBy = extractUserFromToken(authorization);
        log.info("Setting up ClickHouse credentials for project: {} by user: {}", projectId, performedBy);
        
        return clickhouseProjectService.setupProjectClickhouseUser(projectId, performedBy)
            .andThen(Single.just(CredentialsResponse.builder()
                .projectId(projectId)
                .clickhouseUsername(clickhouseProjectService.generateUsername(projectId))
                .isActive(true)
                .message("ClickHouse credentials created. User and row policies configured.")
                .build()))
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Remove ClickHouse credentials for a project.
     * DELETE /v1/projects/{projectId}/clickhouse
     */
    @DELETE
    public CompletionStage<Response<Void>> removeCredentials(
            @PathParam("projectId") String projectId,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        
        String performedBy = extractUserFromToken(authorization);
        log.info("Removing ClickHouse credentials for project: {} by user: {}", projectId, performedBy);
        
        return clickhouseProjectService.removeProjectClickhouseUser(projectId, performedBy)
            .toSingleDefault((Void) null)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Rotate ClickHouse password for a project.
     * POST /v1/projects/{projectId}/clickhouse/rotate
     */
    @POST
    @Path("/rotate")
    public CompletionStage<Response<CredentialsResponse>> rotatePassword(
            @PathParam("projectId") String projectId,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization) {
        
        String performedBy = extractUserFromToken(authorization);
        log.info("Rotating ClickHouse password for project: {} by user: {}", projectId, performedBy);
        
        return clickhouseProjectService.rotateProjectClickhousePassword(projectId, performedBy)
            .andThen(Single.just(CredentialsResponse.builder()
                .projectId(projectId)
                .clickhouseUsername(clickhouseProjectService.generateUsername(projectId))
                .isActive(true)
                .message("ClickHouse password rotated successfully.")
                .build()))
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Get ClickHouse credentials info for a project.
     * GET /v1/projects/{projectId}/clickhouse
     */
    @GET
    public CompletionStage<Response<CredentialsResponse>> getCredentials(
            @PathParam("projectId") String projectId) {
        
        log.info("Getting ClickHouse credentials info for project: {}", projectId);
        
        return clickhouseProjectService.getCredentialsByProjectId(projectId)
            .map(creds -> CredentialsResponse.builder()
                .projectId(creds.getProjectId())
                .clickhouseUsername(creds.getClickhouseUsername())
                .isActive(creds.getIsActive())
                .createdAt(creds.getCreatedAt())
                .message("Credentials found.")
                .build())
            .switchIfEmpty(Single.error(ServiceError.SERVICE_UNKNOWN_EXCEPTION
                .getCustomException("Not found", "No credentials found for project: " + projectId)))
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Get audit history for a project's ClickHouse credentials.
     * GET /v1/projects/{projectId}/clickhouse/audit
     */
    @GET
    @Path("/audit")
    public CompletionStage<Response<AuditHistoryResponse>> getAuditHistory(
            @PathParam("projectId") String projectId) {
        
        log.info("Getting audit history for project: {}", projectId);
        
        return clickhouseProjectService.getAuditHistory(projectId)
            .map(audit -> AuditLogResponse.builder()
                .id(audit.getId())
                .projectId(audit.getProjectId())
                .action(audit.getAction())
                .performedBy(audit.getPerformedBy())
                .details(audit.getDetails())
                .createdAt(audit.getCreatedAt())
                .build())
            .toList()
            .map(logs -> AuditHistoryResponse.builder()
                .logs(logs)
                .count(logs.size())
                .build())
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Get recent audit logs across all projects.
     * GET /v1/projects/clickhouse/audit/recent
     */
    @GET
    @Path("/audit/recent")
    public CompletionStage<Response<AuditHistoryResponse>> getRecentAuditLogs(
            @QueryParam("limit") @DefaultValue("50") Integer limit) {
        
        log.info("Getting recent audit logs with limit: {}", limit);
        
        return clickhouseProjectService.getRecentAuditLogs(limit)
            .map(audit -> AuditLogResponse.builder()
                .id(audit.getId())
                .projectId(audit.getProjectId())
                .action(audit.getAction())
                .performedBy(audit.getPerformedBy())
                .details(audit.getDetails())
                .createdAt(audit.getCreatedAt())
                .build())
            .toList()
            .map(logs -> AuditHistoryResponse.builder()
                .logs(logs)
                .count(logs.size())
                .build())
            .to(RestResponse.jaxrsRestHandler());
    }
    
    private String extractUserFromToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                .getCustomException("Missing authorization", "Authorization header required");
        }
        
        String token = authorization.toLowerCase().startsWith("bearer ") 
            ? authorization.substring(7).trim() 
            : authorization.trim();
        
        try {
            var claims = jwtService.verifyToken(token);
            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", "User ID not found in token");
            }
            return userId;
        } catch (Exception e) {
            throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                .getCustomException("Invalid token", e.getMessage());
        }
    }
}

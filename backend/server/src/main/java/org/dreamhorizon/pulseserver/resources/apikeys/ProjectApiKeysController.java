package org.dreamhorizon.pulseserver.resources.apikeys;

import com.google.inject.Inject;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ApiKeyListRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.CreateApiKeyRestRequest;
import org.dreamhorizon.pulseserver.resources.apikeys.models.CreateApiKeyRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.RevokeApiKeyRestRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;

import java.util.concurrent.CompletionStage;

/**
 * Controller for project API key management.
 * 
 * Public endpoints (for authenticated tenant users):
 * - GET /v1/projects/{projectId}/api-keys - List active API keys (metadata only)
 * - POST /v1/projects/{projectId}/api-keys - Create a new API key (returns raw key once)
 * - DELETE /v1/projects/{projectId}/api-keys/{apiKeyId} - Revoke an API key
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/projects/{projectId}/api-keys")
public class ProjectApiKeysController {

  private static final ApiKeyMapper mapper = ApiKeyMapper.INSTANCE;

  private final ProjectApiKeyService apiKeyService;
  private final JwtService jwtService;

  // ==================== PUBLIC ENDPOINTS ====================

  /**
   * List active API keys for a project (metadata only, no raw key).
   */
  @GET
  @Path("")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ApiKeyListRestResponse>> getActiveApiKeys(
      @NotBlank @PathParam("projectId") String projectId
  ) {
    return apiKeyService.getActiveApiKeys(projectId)
        .map(mapper::toApiKeyListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Create a new API key for a project.
   * Returns the raw API key (only time it's visible to the user).
   */
  @POST
  @Path("")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CreateApiKeyRestResponse>> createApiKey(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @NotBlank @PathParam("projectId") String projectId,
      @NotNull @Valid CreateApiKeyRestRequest request
  ) {
    String createdBy = extractUserEmail(authorization);
    return apiKeyService.createApiKey(mapper.toCreateApiKeyRequest(projectId, request, createdBy))
        .map(mapper::toCreateApiKeyRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Revoke an API key with optional grace period.
   */
  @DELETE
  @Path("/{apiKeyId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<EmptyResponse>> revokeApiKey(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @NotBlank @PathParam("projectId") String projectId,
      @NotNull @PathParam("apiKeyId") Long apiKeyId,
      RevokeApiKeyRestRequest request
  ) {
    String revokedBy = extractUserEmail(authorization);
    RevokeApiKeyRestRequest requestWithDefaults = request != null ? request : new RevokeApiKeyRestRequest();
    
    return apiKeyService.revokeApiKey(mapper.toRevokeApiKeyRequest(projectId, apiKeyId, requestWithDefaults, revokedBy))
        .toSingleDefault(EmptyResponse.emptyResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  // ==================== HELPER METHODS ====================

  private String extractUserEmail(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return "system";
    }
    try {
      Claims claims = jwtService.verifyToken(authorization.substring(7).trim());
      String email = claims.get("email", String.class);
      return email != null ? email : "system";
    } catch (Exception e) {
      log.debug("Failed to extract user email from token: {}", e.getMessage());
      return "system";
    }
  }
}

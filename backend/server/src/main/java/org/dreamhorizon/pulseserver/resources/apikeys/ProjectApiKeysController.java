package org.dreamhorizon.pulseserver.resources.apikeys;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ApiKeyListRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.CreateApiKeyRestRequest;
import org.dreamhorizon.pulseserver.resources.apikeys.models.CreateApiKeyRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.RevokeApiKeyRestRequest;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ValidApiKeyListRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.tenant.TenantContext;

import java.util.concurrent.CompletionStage;

/**
 * Controller for project API key management.
 * 
 * Public endpoints (for authenticated tenant users):
 * - GET /v1/projects/{projectId}/api-keys - List active API keys (metadata only)
 * - POST /v1/projects/{projectId}/api-keys - Create a new API key (returns raw key once)
 * - DELETE /v1/projects/{projectId}/api-keys/{apiKeyId} - Revoke an API key
 * 
 * Internal endpoints:
 * - GET /internal/v1/api-keys/valid - Get all valid API keys (with raw keys, for cron)
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("")
public class ProjectApiKeysController {

  private static final ApiKeyMapper mapper = ApiKeyMapper.INSTANCE;

  private final ProjectApiKeyService apiKeyService;

  // ==================== PUBLIC ENDPOINTS ====================

  /**
   * List active API keys for a project (metadata only, no raw key).
   */
  @GET
  @Path("/v1/projects/{projectId}/api-keys")
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
  @Path("/v1/projects/{projectId}/api-keys")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CreateApiKeyRestResponse>> createApiKey(
      @NotBlank @PathParam("projectId") String projectId,
      @NotNull @Valid CreateApiKeyRestRequest request
  ) {
    String createdBy = getCurrentUserId();
    return apiKeyService.createApiKey(mapper.toCreateApiKeyRequest(projectId, request, createdBy))
        .map(mapper::toCreateApiKeyRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Revoke an API key with optional grace period.
   */
  @DELETE
  @Path("/v1/projects/{projectId}/api-keys/{apiKeyId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Void>> revokeApiKey(
      @NotBlank @PathParam("projectId") String projectId,
      @NotNull @PathParam("apiKeyId") Long apiKeyId,
      RevokeApiKeyRestRequest request
  ) {
    String revokedBy = getCurrentUserId();
    RevokeApiKeyRestRequest requestWithDefaults = request != null ? request : new RevokeApiKeyRestRequest();
    
    return apiKeyService.revokeApiKey(mapper.toRevokeApiKeyRequest(projectId, apiKeyId, requestWithDefaults, revokedBy))
        .toSingleDefault(true)
        .map(v -> (Void) null)
        .to(RestResponse.jaxrsRestHandler());
  }

  // ==================== INTERNAL ENDPOINTS ====================

  /**
   * Get all valid API keys with raw keys (for cron to sync to Redis).
   * Valid means: active OR (inactive but in grace period), AND not expired.
   */
  @GET
  @Path("/internal/v1/api-keys/valid")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ValidApiKeyListRestResponse>> getAllValidApiKeys() {
    return apiKeyService.getAllValidApiKeys()
        .toList()
        .map(mapper::toValidApiKeyListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  // ==================== HELPER METHODS ====================

  private String getCurrentUserId() {
    String userId = TenantContext.getUserId();
    return userId != null ? userId : "system";
  }
}


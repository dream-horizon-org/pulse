package org.dreamhorizon.pulseserver.resources.apikeys;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ValidApiKeyListRestResponse;
import org.dreamhorizon.pulseserver.resources.internal.models.CronRedisSyncJobAcceptedRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.service.cron.CronRedisMaterializationJobService;

/**
 * Internal controller for API key management.
 * 
 * Internal endpoints:
 * - GET /internal/v1/api-keys/valid - Get all valid API keys (with raw keys, for cron)
 * - POST /internal/v1/api-keys/sync-to-redis - Enqueue async Kong Redis API key map sync (HTTP 202)
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/internal/v1/api-keys")
public class InternalApiKeysController {

  private static final ApiKeyMapper mapper = ApiKeyMapper.INSTANCE;

  private final ProjectApiKeyService apiKeyService;
  private final CronRedisMaterializationJobService cronRedisMaterializationJobService;

  /**
   * Get all valid API keys with raw keys (for cron to sync to Redis).
   * Valid means: active OR (inactive but in grace period), AND not expired.
   */
  @GET
  @Path("/valid")
  @RequiresPermission(Constants.PERMISSION_SUPERADMIN)
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ValidApiKeyListRestResponse>> getAllValidApiKeys() {
    return apiKeyService.getAllValidApiKeys()
        .toList()
        .map(mapper::toValidApiKeyListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Enqueues loading all valid API keys from MySQL and atomically replacing the Kong API key hash in
   * Redis. Returns HTTP 202 Accepted immediately; work runs in the background. Duplicate requests
   * while a non-stale job is in progress are deduplicated.
   */
  @POST
  @Path("/sync-to-redis")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CronRedisSyncJobAcceptedRestResponse>> syncApiKeysToRedis() {
    return cronRedisMaterializationJobService
        .acceptApiKeysSyncToRedis()
        .to(RestResponse.jaxrsRestHandler(
            jakarta.ws.rs.core.Response.Status.ACCEPTED.getStatusCode()));
  }
}

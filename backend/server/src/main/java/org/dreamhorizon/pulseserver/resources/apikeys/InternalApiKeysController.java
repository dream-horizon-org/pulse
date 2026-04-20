package org.dreamhorizon.pulseserver.resources.apikeys;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ApiKeyRedisSyncRestResponse;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ValidApiKeyListRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;
import org.dreamhorizon.pulseserver.service.kong.KongApiKeyRedisSyncService;

import java.util.concurrent.CompletionStage;

/**
 * Internal controller for API key management.
 * 
 * Internal endpoints:
 * - GET /internal/v1/api-keys/valid - Get all valid API keys (with raw keys, for cron)
 * - POST /internal/v1/api-keys/sync-to-redis - Load valid keys and atomically replace Kong Redis hash
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/internal/v1/api-keys")
public class InternalApiKeysController {

  private static final ApiKeyMapper mapper = ApiKeyMapper.INSTANCE;

  private final ProjectApiKeyService apiKeyService;
  private final KongApiKeyRedisSyncService kongApiKeyRedisSyncService;

  /**
   * Get all valid API keys with raw keys (for cron to sync to Redis).
   * Valid means: active OR (inactive but in grace period), AND not expired.
   */
  @GET
  @Path("/valid")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ValidApiKeyListRestResponse>> getAllValidApiKeys() {
    return apiKeyService.getAllValidApiKeys()
        .toList()
        .map(mapper::toValidApiKeyListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Loads all valid API keys from MySQL and atomically replaces {@link Constants#KONG_API_KEY_MAP_REDIS_KEY}
   * in Redis (MULTI / DEL / HSET / EXEC). Intended for pulse-alerts-cron on a schedule.
   */
  @POST
  @Path("/sync-to-redis")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ApiKeyRedisSyncRestResponse>> syncApiKeysToRedis() {
    long startMs = System.currentTimeMillis();
    return kongApiKeyRedisSyncService.syncValidApiKeysToRedis()
        .map(keysSynced -> ApiKeyRedisSyncRestResponse.builder()
            .keysSynced(keysSynced)
            .durationMs(System.currentTimeMillis() - startMs)
            .redisKey(Constants.KONG_API_KEY_MAP_REDIS_KEY)
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }
}

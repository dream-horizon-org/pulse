package org.dreamhorizon.pulseserver.resources.apikeys;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.apikeys.models.ValidApiKeyListRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.apikey.ProjectApiKeyService;

import java.util.concurrent.CompletionStage;

/**
 * Internal controller for API key management.
 * 
 * Internal endpoints:
 * - GET /internal/v1/api-keys/valid - Get all valid API keys (with raw keys, for cron)
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/internal/v1/api-keys")
public class InternalApiKeysController {

  private static final ApiKeyMapper mapper = ApiKeyMapper.INSTANCE;

  private final ProjectApiKeyService apiKeyService;

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
}

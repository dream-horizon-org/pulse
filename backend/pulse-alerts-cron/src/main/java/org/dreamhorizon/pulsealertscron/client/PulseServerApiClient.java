package org.dreamhorizon.pulsealertscron.client;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.config.ApplicationConfig;
import org.dreamhorizon.pulsealertscron.dto.response.ApiKeysResponse;
import org.dreamhorizon.pulsealertscron.dto.response.UsageLimitsApiResponse;

@Slf4j
public class PulseServerApiClient {
  private final WebClient webClient;
  private final String apiBaseUrl;
  private final String serviceJwt;
  
  private static final String ACTIVE_LIMITS_PATH = "/internal/v1/usage-limits/active";
  private static final String VALID_API_KEYS_PATH = "/internal/v1/api-keys/valid";
  private static final long REQUEST_TIMEOUT_MS = 30000;

  @Inject
  public PulseServerApiClient(WebClient webClient, ApplicationConfig config) {
    this.webClient = webClient;
    this.apiBaseUrl = config.getPulseServerUrl();
    this.serviceJwt = config.getServiceJwtSecret();
  }

  public Single<UsageLimitsApiResponse.Response> getActiveLimits() {
    log.info("Fetching active usage limits from API: {}", apiBaseUrl + ACTIVE_LIMITS_PATH);
    
    return Single.defer(() -> 
        webClient
            .getAbs(apiBaseUrl + ACTIVE_LIMITS_PATH)
            .putHeader("Authorization", "Bearer " + serviceJwt)
            .timeout(REQUEST_TIMEOUT_MS)
            .rxSend()
            .map(response -> {
              int statusCode = response.statusCode();
              
              if (statusCode != 200) {
                String errorMsg = String.format(
                    "API returned status %d: %s", 
                    statusCode, 
                    response.bodyAsString()
                );
                log.error("❌ Failed to fetch usage limits: {}", errorMsg);
                throw new RuntimeException(errorMsg);
              }
              
              UsageLimitsApiResponse.Response result = response.bodyAsJsonObject()
                  .mapTo(UsageLimitsApiResponse.Response.class);
              
              log.info("✅ Successfully fetched {} active usage limits", result.getCount());
              return result;
            })
            .doOnError(error -> 
                log.error("❌ Error calling usage limits API", error)
            )
    );
  }

  public Single<ApiKeysResponse.Response> getValidApiKeys() {
    log.info("Fetching valid API keys from: {}", apiBaseUrl + VALID_API_KEYS_PATH);
    
    return Single.defer(() -> 
        webClient
            .getAbs(apiBaseUrl + VALID_API_KEYS_PATH)
            .putHeader("Authorization", "Bearer " + serviceJwt)
            .timeout(REQUEST_TIMEOUT_MS)
            .rxSend()
            .map(response -> {
              int statusCode = response.statusCode();
              
              if (statusCode != 200) {
                String errorMsg = String.format(
                    "API returned status %d: %s", 
                    statusCode, 
                    response.bodyAsString()
                );
                log.error("❌ Failed to fetch API keys: {}", errorMsg);
                throw new RuntimeException(errorMsg);
              }
              
              ApiKeysResponse.Response result = response.bodyAsJsonObject()
                  .mapTo(ApiKeysResponse.Response.class);
              
              log.info("✅ Successfully fetched {} valid API keys", result.getCount());
              return result;
            })
            .doOnError(error -> 
                log.error("❌ Error calling API keys API", error)
            )
    );
  }
}

package org.dreamhorizon.pulseserver.client.emr;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.EmrServerlessConfig;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.emrserverless.EmrServerlessClient;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.GetJobRunResponse;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunRequest;
import software.amazon.awssdk.services.emrserverless.model.StartJobRunResponse;

/**
 * Thin wrapper around {@link EmrServerlessClient} for batch orchestration (Phase 2).
 * Uses the same default AWS credential chain as other pulse-server SDK clients.
 */
@Slf4j
@Singleton
public class EmrServerlessJobClient implements AutoCloseable {

  private final EmrServerlessConfig config;
  private final EmrServerlessClient client;

  @Inject
  public EmrServerlessJobClient(EmrServerlessConfig config) {
    this.config = config;
    if (config.isEnabled()) {
      this.client = EmrServerlessClient.builder()
          .region(Region.of(config.getEffectiveRegion()))
          .credentialsProvider(DefaultCredentialsProvider.create())
          .httpClient(UrlConnectionHttpClient.builder().build())
          .build();
      log.info(
          "[EmrServerlessJobClient] Initialized region={} applicationId={}",
          config.getEffectiveRegion(),
          config.getApplicationId());
    } else {
      this.client = null;
      log.info("[EmrServerlessJobClient] EMR Serverless integration is disabled");
    }
  }

  public boolean isEnabled() {
    return config.isEnabled();
  }

  public StartJobRunResponse startJobRun(StartJobRunRequest request) {
    ensureEnabled();
    return client.startJobRun(request);
  }

  public GetJobRunResponse getJobRun(GetJobRunRequest request) {
    ensureEnabled();
    return client.getJobRun(request);
  }

  /**
   * Pre-fills {@code applicationId} and optional {@code executionRoleArn} from config.
   * Callers add job name, Spark submit parameters, etc.
   */
  public StartJobRunRequest.Builder startJobRunRequestBuilder() {
    ensureEnabled();
    StartJobRunRequest.Builder builder = StartJobRunRequest.builder()
        .applicationId(config.getApplicationId());
    if (config.getExecutionRoleArn() != null && !config.getExecutionRoleArn().isBlank()) {
      builder.executionRoleArn(config.getExecutionRoleArn());
    }
    return builder;
  }

  public String getApplicationId() {
    return config.getApplicationId();
  }

  private void ensureEnabled() {
    if (!config.isEnabled() || client == null) {
      throw new IllegalStateException(
          "EMR Serverless is disabled; set emrServerless.enabled and required fields in configuration");
    }
  }

  @Override
  public void close() {
    if (client != null) {
      client.close();
    }
  }
}

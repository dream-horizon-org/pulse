package org.dreamhorizon.pulseserver.resources.configs;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.models.*;
import org.dreamhorizon.pulseserver.util.CompletableFutureUtils;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/configs")
public class ConfigController {
  private final ConfigService configService;
  private final ApplicationConfig applicationConfig;
  private static final RestConfigMapper mapper = RestConfigMapper.INSTANCE;

  @GET
  @Path("/{version}")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<PulseConfig>> getSdkConfig(@PathParam("version") Integer version) {
    return configService.getSdkConfig(ProjectContext.getProjectId(), version)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/active")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<PulseConfig> getActiveSdkConfig() {
    String projectId = ProjectContext.getProjectId();
    log.info("Fetching active SDK config for project: {}", projectId);
    return configService.getActiveSdkConfig(projectId)
        .to(CompletableFutureUtils::fromSingle);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CreateConfigResponse>> createSdkConfig(
      @NotNull @HeaderParam("user-email") String user,
      @NotNull @Valid PulseConfig config
  ) {
    applyConfigDefaults(config);
    ConfigData createConfigServiceRequest = mapper.toServiceCreateConfigRequest(config, user);
    return configService.createSdkConfig(ProjectContext.getProjectId(), createConfigServiceRequest)
        .map(resp -> CreateConfigResponse.builder().version(resp.getVersion()).build())
        .to(RestResponse.jaxrsRestHandler());
  }

  private void applyConfigDefaults(PulseConfig config) {
    applyInteractionConfigDefaults(config);
    applySignalsConfigDefaults(config);
    applyFeatureConfigDefaults(config);
  }

  private void applyInteractionConfigDefaults(PulseConfig config) {
    if (config.getInteraction() != null) {
      PulseConfig.InteractionConfig interaction = config.getInteraction();
      if (interaction.getCollectorUrl() == null || interaction.getCollectorUrl().isBlank()) {
        interaction.setCollectorUrl(applicationConfig.getOtelCollectorUrl());
      }
      if (interaction.getConfigUrl() == null || interaction.getConfigUrl().isBlank()) {
        String projectId = ProjectContext.getProjectId();
        String configUrl = String.format("%s/projects/%s/interaction.json", 
            applicationConfig.getInteractionConfigUrl(), projectId);
        interaction.setConfigUrl(configUrl);
      }
    }
  }

  private void applySignalsConfigDefaults(PulseConfig config) {
    if (config.getSignals() != null) {
      PulseConfig.SignalsConfig signals = config.getSignals();
      if (signals.getLogsCollectorUrl() == null || signals.getLogsCollectorUrl().isBlank()) {
        signals.setLogsCollectorUrl(applicationConfig.getLogsCollectorUrl());
      }
      if (signals.getMetricCollectorUrl() == null || signals.getMetricCollectorUrl().isBlank()) {
        signals.setMetricCollectorUrl(applicationConfig.getMetricCollectorUrl());
      }
      if (signals.getSpanCollectorUrl() == null || signals.getSpanCollectorUrl().isBlank()) {
        signals.setSpanCollectorUrl(applicationConfig.getSpanCollectorUrl());
      }
      if (signals.getCustomEventCollectorUrl() == null || signals.getCustomEventCollectorUrl().isBlank()) {
        signals.setCustomEventCollectorUrl(applicationConfig.getCustomEventCollectorUrl());
      }
    }
  }

  private void applyFeatureConfigDefaults(PulseConfig config) {
    if (config.getFeatures() == null) {
      return;
    }
    config.getFeatures().forEach(feature -> {
      if (feature.getFeatureName() == Features.session_replay) {
        feature.setConfig(applySessionReplayDefaults(feature.getConfig()));
      }
    });
  }

  private SessionReplayFeatureConfig applySessionReplayDefaults(FeatureConfigProperties config) {
    SessionReplayFeatureConfig sessionReplayConfig = config != null
        ? (SessionReplayFeatureConfig) config
        : SessionReplayFeatureConfig.builder().build();

    if (sessionReplayConfig.getTextAndInputPrivacy() == null) {
        sessionReplayConfig.setTextAndInputPrivacy(TextAndInputPrivacy.MASK_ALL);
    }
    if (sessionReplayConfig.getImagePrivacy() == null) {
        sessionReplayConfig.setImagePrivacy(ImagePrivacy.MASK_ALL);
    }
    if (sessionReplayConfig.getThrottleDelayMs() == null) {
        sessionReplayConfig.setThrottleDelayMs(1000L);
    }
    if (sessionReplayConfig.getScreenshotScale() == null) {
        sessionReplayConfig.setScreenshotScale(1.0f);
    }
    if (sessionReplayConfig.getScreenshotQuality() == null) {
        sessionReplayConfig.setScreenshotQuality(30);
    }
    if (sessionReplayConfig.getFlushIntervalSeconds() == null) {
        sessionReplayConfig.setFlushIntervalSeconds(60);
    }
    if (sessionReplayConfig.getFlushAt() == null) {
        sessionReplayConfig.setFlushAt(10);
    }
    if (sessionReplayConfig.getMaxBatchSize() == null) {
        sessionReplayConfig.setMaxBatchSize(50);
    }
    if (sessionReplayConfig.getReplayApiBaseUrl() == null || sessionReplayConfig.getReplayApiBaseUrl().isBlank()) {
        sessionReplayConfig.setReplayApiBaseUrl(applicationConfig.getReplayApiBaseUrl());
    }
    return sessionReplayConfig;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AllConfigdetails>> getSdkConfigDescription() {
    return configService.getAllSdkConfigDetails()
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/rules-features")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<RulesAndFeaturesResponse>> getFeatures() {
    return configService.getRulesandFeatures()
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/scopes-sdks")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<GetScopeAndSdksResponse>> getScopeAndSdks() {
    return configService.getScopeAndSdks()
        .to(RestResponse.jaxrsRestHandler());
  }
}

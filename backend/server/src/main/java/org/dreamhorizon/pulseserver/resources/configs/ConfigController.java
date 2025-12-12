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
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import org.dreamhorizon.pulseserver.resources.configs.models.GetScopeAndSdksResponse;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.resources.configs.models.RulesAndFeaturesResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.CreateConfigResponse;


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
  public CompletionStage<Response<Config>> getConfig(@PathParam("version") Integer version) {
    return configService.getConfig(version)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/active-config")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Config>> getActiveConfig() {
    return configService.getActiveConfig()
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CreateConfigResponse>> createConfig(
      @NotNull @HeaderParam("user-email") String user,
      @NotNull @Valid PulseConfig config
  ) {
    applyInteractionConfigDefaults(config);
    ConfigData createConfigServiceRequest = mapper.toServiceCreateConfigRequest(config, user);
    return configService.createConfig(createConfigServiceRequest)
        .map(resp -> CreateConfigResponse.builder().version(resp.getVersion()).build())
        .to(RestResponse.jaxrsRestHandler());
  }

  private void applyInteractionConfigDefaults(PulseConfig config) {
    if (config.getInteraction() != null) {
      PulseConfig.InteractionConfig interaction = config.getInteraction();
      if (interaction.getCollectorUrl() == null || interaction.getCollectorUrl().isBlank()) {
        interaction.setCollectorUrl(applicationConfig.getOtelCollectorUrl());
      }
      if (interaction.getConfigUrl() == null || interaction.getConfigUrl().isBlank()) {
        interaction.setConfigUrl(applicationConfig.getInteractionConfigUrl());
      }
    }
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AllConfigdetails>> getConfigDescription() {
    return configService.getAllConfigDetails()
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

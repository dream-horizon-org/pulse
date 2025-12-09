package org.dreamhorizon.pulseserver.resources.configs;

import com.google.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.CreateConfigResponse;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/configs")
public class ConfigController {
  private final ConfigService configService;
  private static final RestConfigMapper mapper = RestConfigMapper.INSTANCE;

  @GET
  @Path("/{version}")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Config>> getConfig(@PathParam("version") Integer version) {
    return configService.getConfig(version)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/latest-version")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Config>> getLatestVersion() {
    return configService.getActiveConfig()
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CreateConfigResponse>> createConfig(
      @NotNull @HeaderParam("user-email") String user,
      @NotNull PulseConfig config
  ) {
    ConfigData createConfigServiceRequest = mapper.toServiceCreateConfigRequest(config, user);
    return configService.createConfig(createConfigServiceRequest)
        .map(resp -> CreateConfigResponse.builder().version(resp.getVersion()).build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AllConfigdetails>> getConfigDescription() {
    return configService.getAllConfigDetails()
        .to(RestResponse.jaxrsRestHandler());
  }
}

package org.dreamhorizon.pulseserver.resources.configs;

import com.google.inject.Inject;
import jakarta.ws.rs.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.configs.models.Config;
import org.dreamhorizon.pulseserver.service.configs.ConfigService;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;


@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/configs")
public class ConfigController {
    private final ConfigService configService;
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
      return configService.getConfig()
          .to(RestResponse.jaxrsRestHandler());
    }
}

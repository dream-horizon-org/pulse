package org.dreamhorizon.pulsealertscron.rest;

import com.google.inject.Inject;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulsealertscron.dao.HealthCheckDao;
import org.dreamhorizon.pulsealertscron.rest.io.Response;
import org.dreamhorizon.pulsealertscron.rest.io.RestResponse;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/healthcheck")
public class HealthCheckController {
  final HealthCheckDao healthCheckDao;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<JsonObject>> healthCheck() {
    return healthCheckDao.maintenanceHealthCheck()
        .to(RestResponse.jaxrsRestHandler());
  }
}

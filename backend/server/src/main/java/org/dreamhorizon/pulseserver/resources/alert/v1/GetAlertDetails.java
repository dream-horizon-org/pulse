package org.dreamhorizon.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.alert.AlertMapper;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertDetailsResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/{id}")
public class GetAlertDetails {
  private final AlertService alertsService;

  private static final AlertMapper mapper = AlertMapper.INSTANCE;

  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertDetailsResponseDto>> getAlertDetails(@NotNull @PathParam("id") Integer alertId) {
    return alertsService
        .getAlertDetails(alertId)
        .map(mapper::toAlertDetailsResponseDto)
        .to(RestResponse.jaxrsRestHandler());
  }
}

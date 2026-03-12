package org.dreamhorizon.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertSeverityResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;

@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/severity")
public class GetAlertSeverityList {
  final AlertService alertsService;

  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<AlertSeverityResponseDto>>> getAlertSeverityList() {
    return alertsService
        .getAlertSeverities()
        .to(RestResponse.jaxrsRestHandler());
  }
}

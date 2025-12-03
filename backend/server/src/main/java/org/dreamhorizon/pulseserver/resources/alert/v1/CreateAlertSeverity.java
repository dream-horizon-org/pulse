package org.dreamhorizon.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dto.request.alerts.CreateAlertSeverityRequestDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;

@Data
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/severity")
public class CreateAlertSeverity {
  final AlertService alertsService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> createAlertSeverity(@NotNull CreateAlertSeverityRequestDto createAlertSeverityRequestDto) {
    return alertsService
        .createAlertSeverity(createAlertSeverityRequestDto)
        .to(RestResponse.jaxrsRestHandler());
  }
}

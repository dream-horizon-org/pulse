package in.horizonos.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import in.horizonos.pulseserver.dto.request.alerts.AlertTagMapRequestDto;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.alert.core.AlertService;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/{alert_id}/tag")
public class DeleteTagFromAlert {
  final AlertService alertsService;

  @DELETE
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> deleteTagFromAlert(@PathParam("alert_id") Integer alertId, @NotNull AlertTagMapRequestDto tag) {
    return alertsService
        .deleteAlertTagMapping(alertId, tag)
        .to(RestResponse.jaxrsRestHandler());
  }
}

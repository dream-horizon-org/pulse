package in.horizonos.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import in.horizonos.pulseserver.dto.response.alerts.AlertEvaluationHistoryResponseDto;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.alert.core.AlertService;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/{id}/evaluationHistory")
public class GetAlertEvaluationHistory {
  private final AlertService alertsService;

  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<AlertEvaluationHistoryResponseDto>>> getAlertEvaluationHistory(
      @NotNull @HeaderParam("authorization") String authorization, @NotNull @PathParam("id") Integer alertId) {
    return alertsService
        .getAlertEvaluationHistory(alertId)
        .to(RestResponse.jaxrsRestHandler());
  }
}

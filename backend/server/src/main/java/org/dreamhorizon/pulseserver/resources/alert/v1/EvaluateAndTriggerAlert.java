package org.dreamhorizon.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.alert.models.EvaluateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.EvaluateAlertResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertEvaluationService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/evaluateAndTriggerAlert")
public class EvaluateAndTriggerAlert {
  private final AlertEvaluationService alertEvaluationService;

  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<EvaluateAlertResponseDto>> evaluateAndTriggerAlert(@BeanParam EvaluateAlertRequestDto request) {
    return alertEvaluationService.evaluateAlertById(request.getAlertId())
        .to(RestResponse.jaxrsRestHandler());
  }
}
package org.dreamhorizon.pulseserver.resources.alert.v1;

import org.dreamhorizon.pulseserver.dto.v1.request.alerts.EvaluateAlertV4RequestDto;
import org.dreamhorizon.pulseserver.dto.v1.response.alerts.EvaluateAlertV4ResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.v4.AlertEvaluationServiceV4;
import com.google.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletionStage;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/evaluateAndTriggerAlert")
public class EvaluateAndTriggerAlert {
    private final AlertEvaluationServiceV4 alertEvaluationServiceV4;

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response<EvaluateAlertV4ResponseDto>> evaluateAndTriggerAlert(@BeanParam EvaluateAlertV4RequestDto request) {
        return alertEvaluationServiceV4.evaluateAlertById(request.getAlertId())
                .to(RestResponse.jaxrsRestHandler());
    }
}
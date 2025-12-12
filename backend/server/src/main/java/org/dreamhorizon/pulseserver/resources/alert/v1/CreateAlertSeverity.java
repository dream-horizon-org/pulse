package org.dreamhorizon.pulseserver.resources.alert.v1;

import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertSeverityRequestDto;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import com.google.inject.Inject;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;

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

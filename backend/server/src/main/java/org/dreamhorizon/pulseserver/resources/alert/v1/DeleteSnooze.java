package org.dreamhorizon.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthHeaders;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GenericSuccessResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/{id}/snooze")
public class DeleteSnooze {
  final AlertService alertsService;

  @DELETE
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<GenericSuccessResponse>> deleteSnooze(
      @BeanParam @NotNull AuthHeaders authHeaders,
      @PathParam("id") Integer alertId
  ) {
    DeleteSnoozeRequest serviceRequest = DeleteSnoozeRequest
        .builder()
        .alertId(alertId)
        .updatedBy(authHeaders.getUserEmail())
        .build();

    return alertsService.deleteSnooze(serviceRequest)
        .map(res -> new GenericSuccessResponse("success"))
        .to(RestResponse.jaxrsRestHandler());
  }
}


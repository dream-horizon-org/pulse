package org.dreamhorizon.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.alert.AlertMapper;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestRequest;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestResponse;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthHeaders;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/{id}/snooze")
public class SnoozeAlert {
  private static final AlertMapper mapper = AlertMapper.INSTANCE;
  final AlertService alertsService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SnoozeAlertRestResponse>> snoozeAlert(
      @BeanParam @NotNull AuthHeaders authHeaders,
      @PathParam("id") Integer alertId,
      @NotNull @Valid SnoozeAlertRestRequest snoozeAlertRestRequest
  ) {
    SnoozeAlertRequest serviceRequest = mapper.toServiceRequest(authHeaders.getUserEmail(), alertId, snoozeAlertRestRequest);
    return alertsService.snoozeAlert(serviceRequest)
        .map(mapper::toSnoozeAlertRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }
}


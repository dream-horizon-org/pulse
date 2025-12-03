package org.dreamhorizon.pulseserver.resources.alert;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.response.alerts.AlertResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestRequest;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestResponse;
import org.dreamhorizon.pulseserver.resources.alert.models.UpdateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthHeaders;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.service.alert.core.models.CreateAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GenericSuccessResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.UpdateAlertRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v3/alert")
public class AlertController {
  private static final AlertMapper mapper = AlertMapper.INSTANCE;
  final AlertService alertsService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertResponseDto>> createAlertV3(
      @NotNull @Valid CreateAlertRequestDto createAlertRequestDto
  ) {
    CreateAlertRequest serviceRequest = mapper.toCreateAlertRequest(createAlertRequestDto);
    return alertsService
        .createAlert(serviceRequest)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertResponseDto>> updateAlert(
      @NotNull @Valid UpdateAlertRequestDto updateAlertRequestDto
  ) {
    UpdateAlertRequest serviceRequest = mapper.toUpdateAlertRequest(updateAlertRequestDto);
    return alertsService.updateAlert(serviceRequest)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/{alertId}/snooze")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SnoozeAlertRestResponse>> snoozeAlert(
      @BeanParam @NotNull AuthHeaders authHeaders,
      @PathParam("alertId") Integer alertId,
      @NotNull @Valid SnoozeAlertRestRequest snoozeAlertRestRequest
  ) {

    SnoozeAlertRequest serviceRequest = mapper.toServiceRequest(authHeaders.getUserEmail(), alertId, snoozeAlertRestRequest);
    return alertsService.snoozeAlert(serviceRequest)
        .map(mapper::toSnoozeAlertRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{alertId}/snooze")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<GenericSuccessResponse>> deleteSnooze(
      @BeanParam @NotNull AuthHeaders authHeaders,
      @PathParam("alertId") Integer alertId
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
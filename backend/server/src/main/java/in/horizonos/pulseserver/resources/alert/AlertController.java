package in.horizonos.pulseserver.resources.alert;

import com.google.inject.Inject;
import in.horizonos.pulseserver.dto.response.alerts.AlertResponseDto;
import in.horizonos.pulseserver.resources.alert.models.CreateAlertRequestDto;
import in.horizonos.pulseserver.resources.alert.models.SnoozeAlertRestRequest;
import in.horizonos.pulseserver.resources.alert.models.SnoozeAlertRestResponse;
import in.horizonos.pulseserver.resources.alert.models.UpdateAlertRequestDto;
import in.horizonos.pulseserver.resources.v1.auth.models.AuthHeaders;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.alert.core.AlertService;
import in.horizonos.pulseserver.service.alert.core.models.CreateAlertRequest;
import in.horizonos.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import in.horizonos.pulseserver.service.alert.core.models.GenericSuccessResponse;
import in.horizonos.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import in.horizonos.pulseserver.service.alert.core.models.UpdateAlertRequest;
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
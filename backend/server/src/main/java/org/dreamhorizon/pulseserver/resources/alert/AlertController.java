package org.dreamhorizon.pulseserver.resources.alert;

import org.dreamhorizon.pulseserver.resources.alert.models.*;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.UpdateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthHeaders;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.service.alert.core.models.CreateAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GenericSuccessResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.UpdateAlertRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import com.google.inject.Inject;

import java.util.List;
import java.util.concurrent.CompletionStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert")
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

  @GET
  @Path("/{alert_id}/tag")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<AlertTagsResponseDto>>> getTagsForAlert(@NotNull @PathParam("alert_id") Integer alertId) {
    return alertsService
            .getTagsForAlert(alertId)
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/tag")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<AlertTagsResponseDto>>> getAllTags() {
    return alertsService
            .getTags()
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{id: \\d+}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertDetailsResponseDto>> getAlertDetails(@NotNull @PathParam("id") Integer alertId) {
    return alertsService
            .getAlertDetails(alertId)
            .map(mapper::toAlertDetailsResponseDto)
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/severity")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<AlertSeverityResponseDto>>> getAlertSeverityList() {
    return alertsService
            .getAlertSeverities()
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertDetailsPaginatedResponseDto>> getAlerts(
          @BeanParam GetAlertsListRequestDto getAlertsListRequestDto
  ) {
    return alertsService.getAlerts(getAlertsListRequestDto)
            .map(mapper::toAlertDetailsPaginatedResponseDto)
            .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{id: \\d+}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> deleteAlert(@NotNull @PathParam("id") Integer alertId) {
    return alertsService.deleteAlert(alertId)
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/filters")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertFiltersResponseDto>> getAlertsFilters() {
    return alertsService
            .getAlertFilters()
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{id: \\d+}/evaluationHistory")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<AlertEvaluationHistoryResponseDto>>> getAlertEvaluationHistory(
          @NotNull @HeaderParam("authorization") String authorization, @NotNull @PathParam("id") Integer alertId) {
    return alertsService
            .getAlertEvaluationHistory(alertId)
            .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/tag")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> createTag(@NotNull CreateTagRequestDto tag) {
    return alertsService
            .createTag(tag.getTag())
            .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/{alert_id}/tag")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> addTagToAlert(@PathParam("alert_id") Integer alertId, @NotNull AlertTagMapRequestDto alertTagMapRequestDto) {
    return alertsService
            .createTagAndAlertMapping(alertId, alertTagMapRequestDto)
            .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{alert_id}/tag")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> deleteTagFromAlert(@PathParam("alert_id") Integer alertId, @NotNull AlertTagMapRequestDto tag) {
    return alertsService
            .deleteAlertTagMapping(alertId, tag)
            .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/severity")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> createAlertSeverity(@NotNull CreateAlertSeverityRequestDto createAlertSeverityRequestDto) {
    return alertsService
            .createAlertSeverity(createAlertSeverityRequestDto)
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/notificationChannels")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<AlertNotificationChannelResponseDto>>> getAlertNotificationChannels() {
    return alertsService
            .getAlertNotificationChannels()
            .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/notificationChannels")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> createAlertNotificationChannel(@NotNull CreateAlertNotificationChannelRequestDto createAlertNotificationChannelRequestDto) {
    return alertsService
            .createAlertNotificationChannel(createAlertNotificationChannelRequestDto)
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/scopes")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertScopesResponseDto>> getAlertScopes() {
    return alertsService.getAlertScopes()
            .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/metrics")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertMetricsResponseDto>> getAlertMetrics(@QueryParam("scope") @NotNull String scope) {
    return alertsService.getAlertMetrics(scope)
            .to(RestResponse.jaxrsRestHandler());
  }
}

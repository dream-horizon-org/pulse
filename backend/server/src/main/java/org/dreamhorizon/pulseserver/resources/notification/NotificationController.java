package org.dreamhorizon.pulseserver.resources.notification;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationBatchResponseDto;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationLogsResponseDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationController {

  final NotificationService notificationService;

  @POST
  @Path("/send")
  public CompletionStage<Response<NotificationBatchResponseDto>> sendNotification(
      @HeaderParam("X-Project-Id") String projectId,
      @NotNull @Valid SendNotificationRequestDto request) {

    log.debug("Sending notification for project {}, event: {}", projectId, request.getEventName());

    return notificationService
        .sendNotification(projectId, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/send/async")
  public CompletionStage<Response<NotificationBatchResponseDto>> sendNotificationAsync(
      @HeaderParam("X-Project-Id") String projectId,
      @NotNull @Valid SendNotificationRequestDto request) {

    log.debug(
        "Queueing async notification for project {}, event: {}", projectId, request.getEventName());

    return notificationService
        .sendNotificationAsync(projectId, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/logs")
  public CompletionStage<Response<NotificationLogsResponseDto>> getLogs(
      @HeaderParam("X-Project-Id") String projectId,
      @QueryParam("limit") @DefaultValue("50") int limit,
      @QueryParam("offset") @DefaultValue("0") int offset) {

    return notificationService
        .getLogs(projectId, limit, offset)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/logs/idempotency/{idempotencyKey}")
  public CompletionStage<Response<NotificationLogsResponseDto>> getLogsByIdempotencyKey(
      @HeaderParam("X-Project-Id") String projectId,
      @PathParam("idempotencyKey") String idempotencyKey) {

    return notificationService
        .getLogsByIdempotencyKey(projectId, idempotencyKey)
        .to(RestResponse.jaxrsRestHandler());
  }
}

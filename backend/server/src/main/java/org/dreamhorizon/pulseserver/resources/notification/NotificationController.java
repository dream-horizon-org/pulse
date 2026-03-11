package org.dreamhorizon.pulseserver.resources.notification;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.constant.NotificationConstants;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationBatchResponseDto;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationLogsResponseDto;
import org.dreamhorizon.pulseserver.resources.notification.models.RecipientsDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.ContactRequestDto;
import org.dreamhorizon.pulseserver.rest.Error;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.dreamhorizon.pulseserver.util.JwtUtils;

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

  @POST
  @Path("/contact-us")
  public CompletionStage<Response<String>> contactUs(
        @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
        @QueryParam("type") String eventType,
        ContactRequestDto request){
      
      // Validate event type
      String eventName;
      String successMessage;
      if ("sales".equalsIgnoreCase(eventType)) {
          eventName = NotificationConstants.CONTACT_US_EVENT_NAME;
          successMessage = "Contact request submitted successfully";
      } else if ("support".equalsIgnoreCase(eventType)) {
          eventName = NotificationConstants.CONTACT_SUPPORT_EVENT_NAME;
          successMessage = "Support request submitted successfully";
      } else {
          return CompletableFuture.completedFuture(
              Response.errorResponse(
                  Error.of("INVALID_TYPE", "Invalid contact type. Use 'sales' or 'support'"),
                  400
              )
          );
      }
      
      String tenantId = TenantContext.getTenantId();
      String token = authorization.substring("Bearer ".length());
      String userEmail = JwtUtils.extractEmail(token);
      
      // Build params with message (null if not provided)
      Map<String, Object> params = new java.util.HashMap<>();
      params.put("userEmail", userEmail);
      params.put("tenantId", tenantId);
      if (request != null && request.getMessage() != null) {
          params.put("message", request.getMessage());
      } else {
          params.put("message", null);
      }
              
      return notificationService.sendNotificationAsync(
                      "default-project",
              SendNotificationRequestDto.builder()
                      .eventName(eventName)
                      .channelTypes(List.of(ChannelType.EMAIL))
                      .idempotencyKey(UUID.randomUUID().toString())
                      .params(params)
                      .build()
      ).map(res -> successMessage)
              .to(RestResponse.jaxrsRestHandler());
  }
}

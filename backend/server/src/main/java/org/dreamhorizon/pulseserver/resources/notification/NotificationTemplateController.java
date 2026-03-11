package org.dreamhorizon.pulseserver.resources.notification;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.notification.models.*;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/notifications/templates")
public class NotificationTemplateController {

  final NotificationService notificationService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<NotificationTemplateDto>>> getTemplates(
      @QueryParam("channelType") ChannelType channelType) {
    return notificationService.getTemplates(channelType).to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{templateId}")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<NotificationTemplateDto>> getTemplate(
      @PathParam("templateId") Long templateId) {
    return notificationService
        .getTemplate(templateId)
        .toSingle()
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<NotificationTemplateDto>> createTemplate(
      @NotNull @Valid CreateTemplateRequestDto request) {
    return notificationService
        .createTemplate(request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{templateId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<NotificationTemplateDto>> updateTemplate(
      @PathParam("templateId") Long templateId,
      @NotNull @Valid UpdateTemplateRequestDto request) {
    return notificationService
        .updateTemplate(templateId, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{templateId}")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> deleteTemplate(
      @PathParam("templateId") Long templateId) {
    return notificationService.deleteTemplate(templateId).to(RestResponse.jaxrsRestHandler());
  }
}

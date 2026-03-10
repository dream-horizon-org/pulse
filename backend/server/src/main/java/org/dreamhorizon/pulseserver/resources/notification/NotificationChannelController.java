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
import io.reactivex.rxjava3.core.Maybe;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/projects/{projectId}/notification-channels")
public class NotificationChannelController {

  final NotificationService notificationService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<NotificationChannelDto>>> getChannels(
      @PathParam("projectId") String projectId) {
    return notificationService.getChannels(projectId).to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{channelId}")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<NotificationChannelDto>> getChannel(
      @PathParam("projectId") String projectId, @PathParam("channelId") Long channelId) {
    return notificationService
        .getChannel(channelId)
        .switchIfEmpty(Maybe.error(ServiceError.NOT_FOUND.getCustomException("Channel not found")))
        .toSingle()
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<NotificationChannelDto>> createChannel(
      @PathParam("projectId") String projectId, @NotNull @Valid CreateChannelRequestDto request) {
    return notificationService
        .createChannel(projectId, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{channelId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<NotificationChannelDto>> updateChannel(
      @PathParam("projectId") String projectId,
      @PathParam("channelId") Long channelId,
      @NotNull @Valid UpdateChannelRequestDto request) {
    return notificationService
        .updateChannel(channelId, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{channelId}")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> deleteChannel(
      @PathParam("projectId") String projectId, @PathParam("channelId") Long channelId) {
    return notificationService.deleteChannel(channelId).to(RestResponse.jaxrsRestHandler());
  }
}

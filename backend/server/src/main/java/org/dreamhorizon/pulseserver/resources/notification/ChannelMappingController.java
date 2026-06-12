package org.dreamhorizon.pulseserver.resources.notification;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/notifications/channels/mappings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChannelMappingController {

  final NotificationService notificationService;

  @GET
  public CompletionStage<Response<List<ChannelEventMappingDto>>> getMappings(
      @HeaderParam("X-Project-Id")
      @NotBlank(message = "X-Project-Id header is required")
      String projectId) {
    return notificationService.getMappings(projectId).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  public CompletionStage<Response<ChannelEventMappingDto>> createMapping(
      @HeaderParam("X-Project-Id")
      @NotBlank(message = "X-Project-Id header is required")
      String projectId,
      @NotNull @Valid CreateMappingRequestDto request) {
    return notificationService
        .createMapping(projectId, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Path("/batch")
  public CompletionStage<Response<List<ChannelEventMappingDto>>> createMappingsBatch(
      @HeaderParam("X-Project-Id")
      @NotBlank(message = "X-Project-Id header is required")
      String projectId,
      @NotNull @Valid BatchCreateMappingRequestDto request) {
    return notificationService
        .createMappingsBatch(projectId, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{mappingId}")
  public CompletionStage<Response<ChannelEventMappingDto>> updateMapping(
      @PathParam("mappingId") Long mappingId,
      @NotNull @Valid UpdateMappingRequestDto request) {
    return notificationService
        .updateMapping(mappingId, request)
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{mappingId}")
  public CompletionStage<Response<Boolean>> deleteMapping(
      @PathParam("mappingId") Long mappingId) {
    return notificationService.deleteMapping(mappingId).to(RestResponse.jaxrsRestHandler());
  }
}

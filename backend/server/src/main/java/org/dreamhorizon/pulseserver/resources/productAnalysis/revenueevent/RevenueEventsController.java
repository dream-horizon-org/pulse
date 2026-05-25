package org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.CreateRevenueEventRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.RevenueEventListResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.RevenueEventResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.revenueevent.models.UpdateRevenueEventRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.productAnalysis.revenueevent.RevenueEventService;

@Slf4j
@Path("/v1/revenue-events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RevenueEventsController {

  private final RevenueEventService revenueEventService;

  @GET
  @RequiresPermission("can_view")
  public CompletionStage<Response<RevenueEventListResponse>> listRevenueEvents(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required")
      String projectId) {
    return revenueEventService.list(projectId).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @RequiresPermission("can_edit")
  public CompletionStage<Response<RevenueEventResponse>> createRevenueEvent(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required")
      String projectId,
    @HeaderParam("user-email") String userEmail,
    @NotNull @Valid CreateRevenueEventRequest request) {
    return revenueEventService
      .create(projectId, request, userEmail)
      .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @RequiresPermission("can_edit")
  @Path("/{id}")
  public CompletionStage<Response<String>> updateRevenueEvent(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required")
      String projectId,
    @HeaderParam("user-email") String userEmail,
    @PathParam("id") String id,
    @NotNull @Valid UpdateRevenueEventRequest request) {
    return revenueEventService
      .update(projectId, id, request, userEmail)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }

  @DELETE
  @RequiresPermission("can_edit")
  @Path("/{id}")
  public CompletionStage<Response<String>> deleteRevenueEvent(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required")
      String projectId,
    @PathParam("id") String id) {
    return revenueEventService
      .delete(projectId, id)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }
}

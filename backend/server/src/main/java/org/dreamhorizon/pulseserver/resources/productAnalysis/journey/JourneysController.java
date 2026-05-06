package org.dreamhorizon.pulseserver.resources.productAnalysis.journey;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.*;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.ReplaceEntityTagsRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.productAnalysis.journey.JourneyService;

import java.util.concurrent.CompletionStage;

@Slf4j
@Path("/v1/journeys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class JourneysController {

  private final JourneyService journeyService;

  @GET
  @RequiresPermission("can_view")
  public CompletionStage<Response<JourneyListResponse>> listJourneys(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @BeanParam JourneyListQueryParams query) {
    return journeyService.list(projectId, query).to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @RequiresPermission("can_view")
  @Path("/{id}")
  public CompletionStage<Response<JourneyResponse>> getJourney(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id) {
    return journeyService.get(projectId, id).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @RequiresPermission("can_edit")
  public CompletionStage<Response<Long>> createJourney(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @HeaderParam("user-email") String userEmail,
    @NotNull @Valid CreateJourneyRequest request) {
    return journeyService.create(projectId, request, userEmail).to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @RequiresPermission("can_edit")
  @Path("/{id}")
  public CompletionStage<Response<String>> updateJourney(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id,
    @NotNull @Valid UpdateJourneyRequest request) {
    return journeyService
      .update(projectId, id, request)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }

  @DELETE
  @RequiresPermission("can_edit")
  @Path("/{id}")
  public CompletionStage<Response<String>> deleteJourney(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id) {
    return journeyService
      .delete(projectId, id)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }

  /**
   * Stops auto-refresh for an AUTO journey. Flips {@code journey_type} to {@code ONCE} so
   * the daily cron skips it; combined with the latest job's {@code SUCCEEDED} status, the
   * journey reads as {@code COMPLETED} in the listing. Idempotent.
   */
  @POST
  @RequiresPermission("can_edit")
  @Path("/{id: \\d+}/stop")
  public CompletionStage<Response<String>> stopAutoJourney(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id) {
    return journeyService
      .stopAuto(projectId, id)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }

  /**
   * Replaces all tags for the journey (empty {@code tags} clears them). Mappings:
   * {@code funnel_journey_tag}.
   */
  @PUT
  @RequiresPermission("can_edit")
  @Path("/{id}/tags")
  public CompletionStage<Response<String>> replaceJourneyTags(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id,
    @NotNull @Valid ReplaceEntityTagsRequest request) {
    return journeyService
      .replaceTags(projectId, id, request.getTags())
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }
}

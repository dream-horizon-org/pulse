package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.*;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelJourneyTagsListResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.ReplaceEntityTagsRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelDropoffService;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelRcaService;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

@Slf4j
@Path("/v1/funnels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelsController {

  private final FunnelService funnelService;
  private final FunnelDropoffService funnelDropoffService;
  private final FunnelRcaService funnelRcaService;

  /**
   * Distinct funnel/journey tag labels in the project ({@code funnel_journey_tag}). Literal path
   * {@code /tags} must remain before {@code /{id}} routing.
   */
  @GET
  @RequiresPermission("can_view")
  @Path("/tags")
  public CompletionStage<Response<FunnelJourneyTagsListResponse>> listFunnelJourneyTags(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId) {
    return funnelService.listDistinctTags(projectId).to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @RequiresPermission("can_view")
  public CompletionStage<Response<FunnelDefinitionListResponse>> listFunnels(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @BeanParam FunnelListQueryParams query) {
    return funnelService
      .list(projectId, query)
      .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @RequiresPermission("can_view")
  @Path("/{id: \\d+}")
  public CompletionStage<Response<FunnelDefinitionResponse>> getFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id) {
    return funnelService.get(projectId, id).to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @RequiresPermission("can_edit")
  public CompletionStage<Response<Long>> createFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @HeaderParam("user-email") String userEmail,
    @NotNull @Valid CreateFunnelDefinitionRequest request) {
    return funnelService
      .create(projectId, request, userEmail)
      .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @RequiresPermission("can_edit")
  @Path("/{id: \\d+}")
  public CompletionStage<Response<String>> updateFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id,
    @NotNull @Valid UpdateFunnelDefinitionRequest request) {
    return funnelService.update(projectId, id, request)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }

  @DELETE
  @RequiresPermission("can_edit")
  @Path("/{id: \\d+}")
  public CompletionStage<Response<String>> deleteFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id) {
    return funnelService
      .delete(projectId, id)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }

  /**
   * Stops auto-refresh for an AUTO funnel. Flips {@code funnel_type} to {@code ONCE} so
   * the daily cron skips it; combined with the latest job's {@code SUCCEEDED} status, the
   * funnel reads as {@code COMPLETED} in the listing. Idempotent — calling on an already
   * stopped funnel is a no-op success.
   */
  @POST
  @RequiresPermission("can_edit")
  @Path("/{id: \\d+}/stop")
  public CompletionStage<Response<String>> stopAutoFunnel(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id) {
    return funnelService
      .stopAuto(projectId, id)
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }

  /**
   * Replaces all tags for the funnel (empty {@code tags} clears them). Mappings:
   * {@code funnel_journey_tag}.
   */
  /**
   * Tabular funnel RCA payload (precomputed attribution) for async narrative enrichment.
   * {@code focusStepIndex} is the zero-based step the user dropped from.
   */
  @GET
  @RequiresPermission("can_view")
  @Path("/{id: \\d+}/root-cause")
  public CompletionStage<Response<RootCauseResult>> getFunnelRootCause(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required")
          String projectId,
      @PathParam("id") long id,
      @QueryParam("focusStepIndex") int focusStepIndex,
      @QueryParam("runTime") String runTime) {
    return funnelRcaService
        .getFunnelRootCause(projectId, id, focusStepIndex, runTime)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Ranked drop-off causes for one step of the funnel (side-panel payload).
   * {@code runTime} is optional; when absent the service picks the latest run.
   */
  @GET
  @RequiresPermission("can_view")
  @Path("/{id: \\d+}/dropoffs/{stepIndex: \\d+}")
  public CompletionStage<Response<FunnelDropoffResponse>> getFunnelDropoff(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id,
    @PathParam("stepIndex") int stepIndex,
    @QueryParam("runTime") String runTime) {
    return funnelDropoffService
      .getDropoff(projectId, id, stepIndex, runTime)
      .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Loads per-session evidence for a cause the user picked in the side-panel. The
   * query parameter {@code sessionIds} is CSV — typically 5 IDs copied from the
   * cause's {@code exampleSessionIds}.
   */
  @GET
  @RequiresPermission("can_view")
  @Path("/{id: \\d+}/dropoffs/{stepIndex: \\d+}/evidence")
  public CompletionStage<Response<FunnelDropoffEvidenceResponse>> getFunnelDropoffEvidence(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id,
    @PathParam("stepIndex") int stepIndex,
    @QueryParam("runTime") String runTime,
    @QueryParam("sessionIds") String sessionIdsCsv) {
    List<String> sessionIds = parseCsv(sessionIdsCsv);
    return funnelDropoffService
      .getEvidence(projectId, id, stepIndex, runTime, sessionIds)
      .to(RestResponse.jaxrsRestHandler());
  }

  private static List<String> parseCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return Collections.emptyList();
    }
    String[] parts = csv.split(",");
    List<String> out = new java.util.ArrayList<>(parts.length);
    for (String p : parts) {
      String t = p.trim();
      if (!t.isEmpty()) {
        out.add(t);
      }
    }
    return out;
  }

  @PUT
  @RequiresPermission("can_edit")
  @Path("/{id}/tags")
  public CompletionStage<Response<String>> replaceFunnelTags(
    @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required") String projectId,
    @PathParam("id") long id,
    @NotNull @Valid ReplaceEntityTagsRequest request) {
    return funnelService
      .replaceTags(projectId, id, request.getTags())
      .toSingleDefault(Response.successfulResponse("Success"))
      .toCompletionStage();
  }
}

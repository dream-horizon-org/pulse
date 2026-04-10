package org.dreamhorizon.pulseserver.resources.usagelimits;

import com.google.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitPublicRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;

/**
 * Controller for project usage limits - public endpoints.
 * 
 * Public endpoints (for dashboard):
 * - GET /v1/projects/{projectId}/limits - Get project limits (simplified info)
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/projects/{projectId}/limits")
public class ProjectUsageLimitsController {

  private static final UsageLimitMapper mapper = UsageLimitMapper.INSTANCE;

  private final UsageLimitService usageLimitService;

  /**
   * Get project usage limits (public, simplified info for dashboard).
   */
  @GET
  @Path("")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ProjectUsageLimitPublicRestResponse>> getPublicProjectLimits(
      @NotNull @PathParam("projectId") String projectId
  ) {
    return usageLimitService.getProjectLimitsPublic(projectId)
        .map(mapper::toPublicRestResponse)
        .switchIfEmpty(io.reactivex.rxjava3.core.Single.error(
            new RuntimeException("Limits not found for project: " + projectId)))
        .to(RestResponse.jaxrsRestHandler());
  }
}

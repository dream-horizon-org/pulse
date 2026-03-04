package org.dreamhorizon.pulseserver.resources.usagelimits;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectLimitHistoryRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitListRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitPublicRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ResetLimitsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.SetCustomLimitsRestRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ResetLimitsRequest;

/**
 * Controller for project usage limits management.
 * 
 * Public endpoints (for dashboard):
 * - GET /v1/projects/{projectId}/limits - Get project limits (simplified info)
 * 
 * Internal/ endpoints:
 * - GET /internal/v1/projects/{projectId}/limits - Get project limits (full info)
 * - GET /internal/v1/projects/limits - Get all active project limits
 * - PUT /internal/v1/projects/{projectId}/limits - Set custom limits
 * - POST /internal/v1/projects/{projectId}/limits/reset - Reset to tier defaults
 * - GET /internal/v1/projects/{projectId}/limits/history - Get limit change history
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("")
public class ProjectUsageLimitsController {

  private static final UsageLimitMapper mapper = UsageLimitMapper.INSTANCE;

  private final UsageLimitService usageLimitService;

  // ==================== PUBLIC ENDPOINTS ====================

  /**
   * Get project usage limits (public, simplified info for dashboard).
   */
  @GET
  @Path("/v1/projects/{projectId}/limits")
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

  // ==================== INTERNAL/ ENDPOINTS ====================

  /**
   * Get project usage limits (full info for ).
   */
  @GET
  @Path("/internal/v1/projects/{projectId}/limits")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ProjectUsageLimitRestResponse>> getProjectLimits(
      @NotNull @PathParam("projectId") String projectId
  ) {
    return usageLimitService.getProjectLimits(projectId)
        .map(mapper::toRestResponse)
        .switchIfEmpty(io.reactivex.rxjava3.core.Single.error(
            new RuntimeException("Limits not found for project: " + projectId)))
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Get all active project usage limits.
   */
  @GET
  @Path("/internal/v1/projects/limits")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ProjectUsageLimitListRestResponse>> getAllActiveLimits(
      @QueryParam("activeOnly") @DefaultValue("true") Boolean activeOnly
  ) {
    var flowable = activeOnly
        ? usageLimitService.getAllActiveLimits()
        : usageLimitService.getAllLimits();

    return flowable
        .toList()
        .map(mapper::toListRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Set custom limits for a project (internal  only).
   * Supports partial updates - only provided limits are changed.
   * Validates that the project's tenant is on enterprise tier.
   */
  @PUT
  @Path("/internal/v1/projects/{projectId}/limits")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ProjectUsageLimitRestResponse>> setCustomLimits(
      @NotNull @PathParam("projectId") String projectId,
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull @Valid SetCustomLimitsRestRequest request
  ) {
    return usageLimitService.setCustomLimits(mapper.toSetCustomLimitsRequest(projectId, request, userEmail))
        .map(mapper::toRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Reset project limits to tier defaults (internal  only).
   * If tierId is not provided in request, defaults to free tier (1).
   */
  @POST
  @Path("/internal/v1/projects/{projectId}/limits/reset")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ProjectUsageLimitRestResponse>> resetToDefaults(
      @NotNull @PathParam("projectId") String projectId,
      @NotNull @HeaderParam("user-email") String userEmail,
      @Valid ResetLimitsRestRequest request
  ) {
    // Handle null request (no body provided)
    ResetLimitsRestRequest effectiveRequest = request != null ? request : new ResetLimitsRestRequest();
    
    return usageLimitService.resetToDefaults(
            mapper.toResetLimitsRequest(projectId, effectiveRequest, userEmail))
        .map(mapper::toRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Get limit change history for a project (internal  only).
   */
  @GET
  @Path("/internal/v1/projects/{projectId}/limits/history")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ProjectLimitHistoryRestResponse>> getProjectLimitHistory(
      @NotNull @PathParam("projectId") String projectId
  ) {
    return usageLimitService.getProjectLimitHistory(projectId)
        .toList()
        .map(history -> mapper.toHistoryRestResponse(projectId, history))
        .to(RestResponse.jaxrsRestHandler());
  }
}


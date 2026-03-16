package org.dreamhorizon.pulseserver.resources.usagelimits;

import com.google.inject.Inject;
import io.jsonwebtoken.Claims;
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
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.MarkNotificationsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.NotificationStatusRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectLimitHistoryRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitListRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ResetLimitsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.SetCustomLimitsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.UsageNotificationRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;

/**
 * Controller for project usage limits - internal endpoints.
 * 
 * Internal endpoints:
 * - GET /internal/v1/projects/{projectId}/limits - Get project limits (full info)
 * - GET /internal/v1/projects/limits - Get all active project limits
 * - GET /internal/v1/projects/limits/notifications - Get usage notifications due
 * - PUT /internal/v1/projects/{projectId}/limits - Set custom limits
 * - POST /internal/v1/projects/{projectId}/limits/reset - Reset to tier defaults
 * - GET /internal/v1/projects/{projectId}/limits/history - Get limit change history
 * - POST /internal/v1/projects/{projectId}/limits/notifications - Mark thresholds as notified
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/internal/v1/projects")
public class InternalUsageLimitsController {

  private static final UsageLimitMapper mapper = UsageLimitMapper.INSTANCE;

  private final UsageLimitService usageLimitService;
  private final JwtService jwtService;

  /**
   * Get project usage limits (full info for internal use).
   */
  @GET
  @Path("/{projectId}/limits")
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
  @Path("/limits")
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
   * Get usage notifications that need to be sent (internal only).
   * Analyzes all projects and returns list of notifications due.
   * Called by alerts-cron to determine which notifications to send.
   */
  @GET
  @Path("/limits/notifications")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<UsageNotificationRestResponse>> getUsageNotifications() {
    return usageLimitService.getUsageNotifications()
        .map(mapper::toUsageNotificationResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Set custom limits for a project (internal only).
   * Supports partial updates - only provided limits are changed.
   * Validates that the project's tenant is on enterprise tier.
   */
  @PUT
  @Path("/{projectId}/limits")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ProjectUsageLimitRestResponse>> setCustomLimits(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @NotNull @PathParam("projectId") String projectId,
      @NotNull @Valid SetCustomLimitsRestRequest request
  ) {
    String performedBy = extractUserEmail(authorization);
    return usageLimitService.setCustomLimits(mapper.toSetCustomLimitsRequest(projectId, request, performedBy))
        .map(mapper::toRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Reset project limits to tier defaults (internal only).
   * If tierId is not provided in request, defaults to free tier (1).
   */
  @POST
  @Path("/{projectId}/limits/reset")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<ProjectUsageLimitRestResponse>> resetToDefaults(
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
      @NotNull @PathParam("projectId") String projectId,
      @Valid ResetLimitsRestRequest request
  ) {
    String performedBy = extractUserEmail(authorization);
    ResetLimitsRestRequest effectiveRequest = request != null ? request : new ResetLimitsRestRequest();
    
    return usageLimitService.resetToDefaults(
            mapper.toResetLimitsRequest(projectId, effectiveRequest, performedBy))
        .map(mapper::toRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Get limit change history for a project (internal only).
   */
  @GET
  @Path("/{projectId}/limits/history")
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

  /**
   * Mark specific thresholds as notified for the current month (internal only).
   * Used by alerts cron to track which notifications have been sent.
   */
  @POST
  @Path("/{projectId}/limits/notifications")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<NotificationStatusRestResponse>> markThresholdsNotified(
      @NotNull @PathParam("projectId") String projectId,
      @NotNull @Valid MarkNotificationsRestRequest request
  ) {
    return usageLimitService.markThresholdsNotified(projectId, request.getThresholds())
        .map(response -> NotificationStatusRestResponse.builder()
            .projectId(response.getProjectId())
            .month(response.getMonth())
            .thresholdsNotified(response.getThresholdsNotified())
            .createdAt(response.getCreatedAt())
            .updatedAt(response.getUpdatedAt())
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  // ==================== HELPER METHODS ====================

  private String extractUserEmail(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return "system";
    }
    try {
      Claims claims = jwtService.verifyToken(authorization.substring(7).trim());
      String email = claims.get("email", String.class);
      return email != null ? email : "system";
    } catch (Exception e) {
      log.debug("Failed to extract user email from token: {}", e.getMessage());
      return "system";
    }
  }
}

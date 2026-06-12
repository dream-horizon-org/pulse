package org.dreamhorizon.pulseserver.resources.analytics;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchService;

import java.util.concurrent.CompletionStage;

/**
 * Internal controller for triggering analytics batch jobs.
 *
 * Internal endpoints:
 * - POST /internal/analytics/funnels - Trigger daily funnel batch job
 * - POST /internal/analytics/journeys - Trigger daily journey batch job
 * - POST /internal/analytics/events - Trigger incremental events batch job
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/internal/analytics")
public final class InternalAnalyticsController {

  /**
   * Service for triggering analytics batch jobs.
   */
  private final AnalyticsBatchService analyticsBatchService;

  /**
   * Triggers the daily funnel batch job.
   *
   * @return a response indicating success
   */
  @POST
  @Path("/funnels")
  @RequiresPermission(Constants.PERMISSION_SUPERADMIN)
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> triggerFunnelsBatch() {
    log.info("Received request to trigger funnels batch job");
    return analyticsBatchService.triggerFunnelsBatch()
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Triggers the daily journey batch job.
   *
   * @return a response indicating success
   */
  @POST
  @Path("/journeys")
  @RequiresPermission(Constants.PERMISSION_SUPERADMIN)
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> triggerJourneysBatch() {
    log.info("Received request to trigger journeys batch job");
    return analyticsBatchService.triggerJourneysBatch()
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Triggers the incremental events batch job.
   *
   * @return a response indicating success
   */
  @POST
  @Path("/events")
  @RequiresPermission(Constants.PERMISSION_SUPERADMIN)
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> triggerEventsBatch() {
    log.info("Received request to trigger events batch job");
    return analyticsBatchService.triggerEventsBatch()
        .to(RestResponse.jaxrsRestHandler());
  }
}

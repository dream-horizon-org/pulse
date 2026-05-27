package org.dreamhorizon.pulseserver.resources.screen;

import com.google.inject.Inject;
import com.dream11.rest.annotation.Timeout;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaService;
import org.dreamhorizon.pulseserver.service.rootcause.models.ScreenRcaV2Response;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/screens")
public class ScreenRcaController {

  private final ScreenRcaService screenRcaService;

  // Default lookback for Screen RCA v2. Future: inject from config.
  private static final int DEFAULT_LOOKBACK_DAYS = 7;

  /**
   * Screen RCA v2: UI sends current timestamp as {@code asOf}; Window = [windowEnd − 7d, windowEnd].
   * Returns list of problems ranked by affected user count and problem type priority.
   */
  @GET
  @Path("/{screenName}/root-cause/v2")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  // @RequiresPermission("can_view")
  @Timeout(value = 60000)
  public CompletionStage<Response<ScreenRcaV2Response>> getScreenRootCauseV2(
      @PathParam("screenName") String screenName,
      @QueryParam("windowEnd") String windowEndParam,
      @QueryParam("forceRefresh") String forceRefreshParam) {
    String projectId = ProjectContext.requireProjectId();
    final RootCauseQueryBuilder.Window window;
    final boolean forceRefresh = parseForceRefresh(forceRefreshParam);
    try {
      window = resolveWindowV2(windowEndParam);
    } catch (WebApplicationException e) {
      return CompletableFuture.failedFuture(e);
    }

    LocalDate reportDate = LocalDate.ofInstant(window.endExclusive, ZoneOffset.UTC);
    return screenRcaService
        .getScreenRootCauseV2(projectId, screenName, window, reportDate)
        .to(RestResponse.jaxrsRestHandler());
  }

  private static RootCauseQueryBuilder.Window resolveWindowV2(String windowEndParam) {
    Instant windowEnd = parseRootCauseAsOf(windowEndParam);
    Instant windowStart = windowEnd.minus(DEFAULT_LOOKBACK_DAYS, java.time.temporal.ChronoUnit.DAYS);
    return new RootCauseQueryBuilder.Window(windowStart, windowEnd);
  }

  /** When {@code true}, skips ClickHouse read-through and recomputes tabular screen RCA (regenerate parity). */
  private static boolean parseForceRefresh(String forceRefreshParam) {
    if (forceRefreshParam == null || forceRefreshParam.isBlank()) {
      return false;
    }
    return Boolean.parseBoolean(forceRefreshParam.trim());
  }

  /** Exclusive upper bound on event timestamps; ISO-8601 instant (e.g. {@code 2026-04-07T14:00:00Z}). */
  private static Instant parseRootCauseAsOf(String asOfParam) {
    if (asOfParam == null || asOfParam.isBlank()) {
      return Instant.now();
    }
    return parseQueryInstant(asOfParam, "asOf");
  }

  private static Instant parseQueryInstant(String value, String paramName) {
    try {
      return Instant.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
          "Query parameter '"
              + paramName
              + "' must be a valid ISO-8601 instant (e.g. 2026-04-07T14:00:00Z).",
          e.getMessage());
    }
  }
}

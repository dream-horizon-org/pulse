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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.interaction.models.RootCauseRestResponse;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.service.rootcause.models.ScreenRcaProblemResult;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/screens")
public class ScreenRcaController {

  private final ScreenRcaService screenRcaService;

  @GET
  @Path("/{screenName}/root-cause")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  @Timeout(value = 60000)
  public CompletionStage<Response<RootCauseRestResponse>> getScreenRootCause(
      @PathParam("screenName") String screenName,
      @QueryParam("date") String dateParam,
      @QueryParam("asOf") String asOfParam,
      @QueryParam("forceRefresh") String forceRefreshParam) {
    String projectId = ProjectContext.requireProjectId();
    final LocalDate date;
    final Instant windowEndExclusiveUtc;
    final boolean forceRefresh = parseForceRefresh(forceRefreshParam);
    try {
      date = parseRootCauseQueryDate(dateParam);
      windowEndExclusiveUtc = parseRootCauseAsOf(asOfParam);
    } catch (WebApplicationException e) {
      return CompletableFuture.failedFuture(e);
    }

    return screenRcaService
        .getScreenRootCause(projectId, screenName, date, windowEndExclusiveUtc, forceRefresh)
        .map(this::toRootCauseRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  // Default lookback for Screen RCA v2. Future: inject from config.
  private static final int DEFAULT_LOOKBACK_DAYS = 2;

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
  public CompletionStage<Response<List<ScreenRcaProblemResult>>> getScreenRootCauseV2(
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

    return screenRcaService
        .getScreenRootCauseV2(projectId, screenName, window)
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

  private static LocalDate parseRootCauseQueryDate(String dateParam) {
    if (dateParam == null || dateParam.isBlank()) {
      return LocalDate.now(ZoneOffset.UTC);
    }
    try {
      return LocalDate.parse(dateParam);
    } catch (DateTimeParseException e) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
          "Query parameter 'date' must be a valid ISO-8601 date (yyyy-MM-dd).",
          e.getMessage());
    }
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

  private RootCauseRestResponse toRootCauseRestResponse(RootCauseResult result) {
    List<RootCauseRestResponse.RootCauseSegmentRest> segments = result.getSegments() == null
        ? List.of()
        : result.getSegments().stream()
            .map(this::toRootCauseSegmentRest)
            .collect(Collectors.toList());
    return RootCauseRestResponse.builder()
        .baseline(result.getBaseline())
        .segments(segments)
        .mode(result.getMode())
        .cachedAt(result.getCachedAt())
        .everythingGood(result.getEverythingGood())
        .noDataAvailable(result.getNoDataAvailable())
        .message(result.getMessage())
        .build();
  }

  private RootCauseRestResponse.RootCauseSegmentRest toRootCauseSegmentRest(RootCauseSegment seg) {
    return RootCauseRestResponse.RootCauseSegmentRest.builder()
        .label(seg.getLabel())
        .dimensions(seg.getDimensions())
        .metrics(seg.getMetrics())
        .deltas(seg.getDeltas())
        .build();
  }
}

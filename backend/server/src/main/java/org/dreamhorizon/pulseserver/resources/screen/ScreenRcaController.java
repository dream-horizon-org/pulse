package org.dreamhorizon.pulseserver.resources.screen;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
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
import org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

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
  public CompletionStage<Response<RootCauseRestResponse>> getScreenRootCause(
      @PathParam("screenName") String screenName,
      @QueryParam("date") String dateParam,
      @QueryParam("asOf") String asOfParam,
      @QueryParam("start") String startParam,
      @QueryParam("end") String endParam,
      @DefaultValue("false") @QueryParam("regenerate") boolean regenerate) {
    String projectId = ProjectContext.requireProjectId();

    boolean hasStart = startParam != null && !startParam.isBlank();
    boolean hasEnd = endParam != null && !endParam.isBlank();
    if (hasStart != hasEnd) {
      return CompletableFuture.failedFuture(
          ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
              "Query parameters 'start' and 'end' must both be provided together, or both omitted."));
    }

    if (hasStart) {
      final Instant startInclusive;
      final Instant endExclusive;
      try {
        startInclusive = parseQueryInstant(startParam, "start");
        endExclusive = parseQueryInstant(endParam, "end");
      } catch (WebApplicationException e) {
        return CompletableFuture.failedFuture(e);
      }
      return screenRcaService.getScreenRootCause(
              projectId, screenName, startInclusive, endExclusive, regenerate)
          .map(this::toRootCauseRestResponse)
          .to(RestResponse.jaxrsRestHandler());
    }

    final LocalDate date;
    final Instant windowEndExclusiveUtc;
    try {
      date = parseRootCauseQueryDate(dateParam);
      windowEndExclusiveUtc = parseRootCauseAsOf(asOfParam);
    } catch (WebApplicationException e) {
      return CompletableFuture.failedFuture(e);
    }

    return screenRcaService.getScreenRootCause(
            projectId, screenName, date, windowEndExclusiveUtc, regenerate)
        .map(this::toRootCauseRestResponse)
        .to(RestResponse.jaxrsRestHandler());
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

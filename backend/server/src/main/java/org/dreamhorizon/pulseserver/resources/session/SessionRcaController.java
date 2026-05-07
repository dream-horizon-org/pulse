package org.dreamhorizon.pulseserver.resources.session;

import com.google.inject.Inject;
import com.dream11.rest.annotation.Timeout;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
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
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.interaction.models.RootCauseRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.service.sessionrca.SessionRcaService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/sessions")
public class SessionRcaController {

  private final SessionRcaService sessionRcaService;

  @GET
  @Path("/rca")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  @Timeout(value = 60000)
  public CompletionStage<Response<RootCauseRestResponse>> getSessionRca(
      @QueryParam("date") String dateParam,
      @QueryParam("asOf") String asOfParam,
      @QueryParam("forceRefresh") String forceRefreshParam) {
    String projectId = ProjectContext.requireProjectId();
    final LocalDate date;
    final Instant windowEndExclusiveUtc;
    final boolean forceRefresh = parseForceRefresh(forceRefreshParam);
    try {
      date = parseDate(dateParam);
      windowEndExclusiveUtc = parseAsOf(asOfParam);
    } catch (WebApplicationException e) {
      return CompletableFuture.failedFuture(e);
    }

    return sessionRcaService
        .getSessionRca(projectId, date, windowEndExclusiveUtc, forceRefresh)
        .map(this::toRootCauseRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  private static boolean parseForceRefresh(String param) {
    if (param == null || param.isBlank()) {
      return false;
    }
    return Boolean.parseBoolean(param.trim());
  }

  private static LocalDate parseDate(String dateParam) {
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

  private static Instant parseAsOf(String asOfParam) {
    if (asOfParam == null || asOfParam.isBlank()) {
      return Instant.now();
    }
    try {
      return Instant.parse(asOfParam.trim());
    } catch (DateTimeParseException e) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
          "Query parameter 'asOf' must be a valid ISO-8601 instant (e.g. 2026-04-07T14:00:00Z).",
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

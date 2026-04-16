package org.dreamhorizon.pulseserver.resources.interaction;

import com.google.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.interaction.models.DeleteInteractionRestResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.GetInteractionsRestRequest;
import org.dreamhorizon.pulseserver.resources.interaction.models.GetInteractionsRestResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.InteractionFilterOptionsResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.RestInteractionDetail;
import org.dreamhorizon.pulseserver.resources.interaction.models.ErrorAttributionDrillDownRestResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.ErrorAttributionRestResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.RootCauseRestResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.TelemetryFilterOptionsResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.UpdateInteractionRestResponse;
import org.dreamhorizon.pulseserver.resources.interaction.validators.CreateInteractionValidations;
import org.dreamhorizon.pulseserver.resources.interaction.validators.UpdateInteractionValidations;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.interaction.InteractionService;
import org.dreamhorizon.pulseserver.service.interaction.models.CreateInteractionRequest;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownSignal;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionService;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.service.interaction.models.DeleteInteractionRequest;
import org.dreamhorizon.pulseserver.service.interaction.models.UpdateInteractionRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/interactions")
public class InteractionController {
  private static final RestInteractionMapper mapper = RestInteractionMapper.INSTANCE;

  private final InteractionService interactionService;
  private final Validator validator;
  private final RootCauseConfig rootCauseConfig;
  private final RootCauseService rootCauseService;
  private final ErrorAttributionService errorAttributionService;

  private static WebApplicationException getWebApplicationException(Set<ConstraintViolation<RestInteractionDetail>> violations) {
    return new WebApplicationException(
        jakarta.ws.rs.core.Response.status(400)
            .entity(getErrorMessageFromViolations(violations))
            .type(MediaType.APPLICATION_JSON)
            .build()
    );
  }

  private static Map<String, Object> getErrorMessageFromViolations(Set<ConstraintViolation<RestInteractionDetail>> violations) {
    List<String> messages = violations.stream()
        .map(v -> String.format("%s %s", v.getPropertyPath(), v.getMessage()))
        .collect(Collectors.toList());

    return Map.of("errors", messages);
  }

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<GetInteractionsRestResponse>> getInteractions(
      @BeanParam GetInteractionsRestRequest request
  ) {

    return interactionService.getInteractions(mapper.toServiceRequest(request))
        .map(mapper::toRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_edit")
  public CompletionStage<Response<RestInteractionDetail>> createInteraction(
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull RestInteractionDetail restRequest
  ) {

    Set<ConstraintViolation<RestInteractionDetail>> violations = validator.validate(restRequest, CreateInteractionValidations.class);
    if (!violations.isEmpty()) {
      WebApplicationException badReq = getWebApplicationException(violations);
      return CompletableFuture.failedFuture(badReq);
    }

    CreateInteractionRequest serviceRequest = mapper.toServiceCreateInteractionRequest(restRequest, userEmail);

    return interactionService.createInteraction(serviceRequest)
        .map(resp -> RestInteractionDetail.builder()
            .id(resp.getId())
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @DELETE
  @Path("/{name}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_edit")
  public CompletionStage<Response<DeleteInteractionRestResponse>> deleteInteraction(
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull @PathParam("name") String name
  ) {

    DeleteInteractionRequest serviceRequest = DeleteInteractionRequest.builder()
        .name(name)
        .userEmail(userEmail)
        .build();

    return interactionService.deleteInteraction(serviceRequest)
        .map(res -> DeleteInteractionRestResponse.builder()
            .status(200)
            .build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Path("/{name}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_edit")
  public CompletionStage<Response<UpdateInteractionRestResponse>> updateInteraction(
      @NotNull @HeaderParam("user-email") String userEmail,
      @NotNull @PathParam("name") String name,
      @NotNull RestInteractionDetail restRequest
  ) {

    Set<ConstraintViolation<RestInteractionDetail>> violations = validator.validate(restRequest, UpdateInteractionValidations.class);
    if (!violations.isEmpty()) {
      WebApplicationException badReq = getWebApplicationException(violations);
      return CompletableFuture.failedFuture(badReq);
    }

    UpdateInteractionRequest serviceRequest = mapper.toServiceUpdateInteractionRequest(restRequest, name, userEmail);

    return interactionService.updateInteraction(serviceRequest)
        .map(res -> UpdateInteractionRestResponse.builder().status(200).build())
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{name}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<RestInteractionDetail>> getInteractionDetails(
      @PathParam("name") String name
  ) {

    return interactionService.getInteractionDetails(name)
        .map(mapper::toRestInteractionDetail)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/filter-options")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<InteractionFilterOptionsResponse>> getInteractionFilterOptions() {
    return interactionService.getInteractionFilterOptions()
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/telemetry-filters")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<TelemetryFilterOptionsResponse>> getTelemetryFilterOptions() {
    return interactionService.getTelemetryFilterOptions()
        .to(RestResponse.jaxrsRestHandler());
  }

  // This is used as fallback by pulse-ai if tabular data is not present in /rca/report request
  @GET
  @Path("/{name}/root-cause")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<RootCauseRestResponse>> getRootCause(
      @PathParam("name") String name,
      @QueryParam("date") String dateParam,
      @QueryParam("asOf") String asOfParam
  ) {
    String projectId = ProjectContext.requireProjectId();
    final LocalDate date;
    final Instant windowEndExclusiveUtc;
    try {
      date = parseRootCauseQueryDate(dateParam);
      windowEndExclusiveUtc = parseRootCauseAsOf(asOfParam);
    } catch (WebApplicationException e) {
      return CompletableFuture.failedFuture(e);
    }

    return rootCauseService.getRootCause(projectId, name, date, windowEndExclusiveUtc)
        .map(this::toRootCauseRestResponse)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{name}/error-attribution")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<ErrorAttributionRestResponse>> getErrorAttribution(
      @PathParam("name") String name,
      @QueryParam("start") String startParam,
      @QueryParam("end") String endParam,
      @QueryParam("refresh") @DefaultValue("false") boolean refresh,
      @QueryParam("drillDown") String drillDownParam) {
    String projectId = ProjectContext.requireProjectId();
    final Instant start;
    final Instant end;
    final List<ErrorAttributionDrillDownSignal> drillSignals;
    try {
      start = parseRequiredInstant(startParam, "start");
      end = parseRequiredInstant(endParam, "end");
      if (!end.isAfter(start)) {
        throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
            "'end' must be after 'start'");
      }
      drillSignals = parseDrillDownSignals(drillDownParam);
    } catch (WebApplicationException e) {
      return CompletableFuture.failedFuture(e);
    } catch (IllegalArgumentException e) {
      WebApplicationException bad =
          ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(e.getMessage());
      return CompletableFuture.failedFuture(bad);
    }
    return errorAttributionService
        .getErrorAttributionWithOptionalDrillDown(projectId, name, start, end, refresh, drillSignals)
        .map(
            bundle ->
                toErrorAttributionRestResponse(bundle.summary(), bundle.drillDownBySignal()))
        .to(RestResponse.jaxrsRestHandler());
  }

  private static List<ErrorAttributionDrillDownSignal> parseDrillDownSignals(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    LinkedHashSet<ErrorAttributionDrillDownSignal> set = new LinkedHashSet<>();
    for (String part : raw.split(",")) {
      String t = part.trim();
      if (t.isEmpty()) {
        continue;
      }
      try {
        set.add(ErrorAttributionDrillDownSignal.fromParam(t));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }
    return new ArrayList<>(set);
  }

  private static Instant parseRequiredInstant(String value, String paramName) {
    if (value == null || value.isBlank()) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
          "Query parameter '" + paramName + "' is required and must be a non-blank ISO-8601 instant.");
    }
    try {
      return Instant.parse(value.trim());
    } catch (java.time.format.DateTimeParseException e) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(
          "Query parameter '" + paramName + "' must be a valid ISO-8601 instant (e.g. 2026-04-07T14:00:00Z).",
          e.getMessage());
    }
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

  /** Exclusive upper bound on span timestamps; ISO-8601 instant (e.g. {@code 2026-04-07T14:00:00Z}). */
  private static Instant parseRootCauseAsOf(String asOfParam) {
    if (asOfParam == null || asOfParam.isBlank()) {
      return Instant.now();
    }
    try {
      return Instant.parse(asOfParam);
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

  private ErrorAttributionRestResponse toErrorAttributionRestResponse(
      ErrorAttributionResult result, Map<String, ErrorAttributionDrillDownResult> drillDownBySignal) {
    List<ErrorAttributionRestResponse.RiskRatioEntry> risk =
        result.getRiskRatios() == null
            ? List.of()
            : result.getRiskRatios().stream()
                .map(
                    r ->
                        ErrorAttributionRestResponse.RiskRatioEntry.builder()
                            .signal(r.getSignal())
                            .nTreated(r.getNTreated())
                            .nControl(r.getNControl())
                            .nTreatedLow(r.getNTreatedLow())
                            .nControlLow(r.getNControlLow())
                            .p1(r.getP1())
                            .p2(r.getP2())
                            .rr(r.getRr())
                            .rrUndefined(r.getRrUndefined())
                            .rrUndefinedReason(r.getRrUndefinedReason())
                            .build())
                .collect(Collectors.toList());
    ErrorAttributionRestResponse.ErrorAttributionRestResponseBuilder builder =
        ErrorAttributionRestResponse.builder()
            .trackBInsufficientData(result.getTrackBInsufficientData())
            .minPoorSessionsForErrorAttribution(result.getMinPoorSessionsForErrorAttribution())
            .nPoorInU(result.getNPoorInU())
            .nU(result.getNU())
            .riskRatios(risk)
            .jointWinners(result.getJointWinners())
            .analysisPhase(result.getAnalysisPhase())
            .track(result.getTrack())
            .diagnosticSpecVersion(result.getDiagnosticSpecVersion())
            .cachedAt(result.getCachedAt())
            .disclaimer(result.getDisclaimer());
    if (drillDownBySignal != null && !drillDownBySignal.isEmpty()) {
      Map<String, ErrorAttributionDrillDownRestResponse> restDrill = new LinkedHashMap<>();
      for (Map.Entry<String, ErrorAttributionDrillDownResult> e : drillDownBySignal.entrySet()) {
        restDrill.put(e.getKey(), toErrorAttributionDrillDownRestResponse(e.getValue()));
      }
      builder.drillDown(restDrill);
    }
    return builder.build();
  }

  private ErrorAttributionDrillDownRestResponse toErrorAttributionDrillDownRestResponse(
      ErrorAttributionDrillDownResult result) {
    List<ErrorAttributionDrillDownRestResponse.IssueEntry> issues =
        result.getIssues() == null
            ? null
            : result.getIssues().stream()
                .map(
                    i ->
                        ErrorAttributionDrillDownRestResponse.IssueEntry.builder()
                            .groupId(i.getGroupId())
                            .title(i.getTitle())
                            .occurrences(i.getOccurrences())
                            .exceptionType(i.getExceptionType())
                            .nTreated(i.getNTreated())
                            .nControl(i.getNControl())
                            .nTreatedLow(i.getNTreatedLow())
                            .nControlLow(i.getNControlLow())
                            .p1(i.getP1())
                            .p2(i.getP2())
                            .rr(i.getRr())
                            .rrUndefined(i.getRrUndefined())
                            .rrUndefinedReason(i.getRrUndefinedReason())
                            .build())
                .collect(Collectors.toList());
    List<ErrorAttributionDrillDownRestResponse.NetworkEndpointEntry> nets =
        result.getNetworkEndpoints() == null
            ? null
            : result.getNetworkEndpoints().stream()
                .map(
                    n ->
                        ErrorAttributionDrillDownRestResponse.NetworkEndpointEntry.builder()
                            .url(n.getUrl())
                            .graphqlOperationName(n.getGraphqlOperationName())
                            .graphqlOperationType(n.getGraphqlOperationType())
                            .occurrences(n.getOccurrences())
                            .nTreated(n.getNTreated())
                            .nControl(n.getNControl())
                            .nTreatedLow(n.getNTreatedLow())
                            .nControlLow(n.getNControlLow())
                            .p1(n.getP1())
                            .p2(n.getP2())
                            .rr(n.getRr())
                            .rrUndefined(n.getRrUndefined())
                            .rrUndefinedReason(n.getRrUndefinedReason())
                            .build())
                .collect(Collectors.toList());
    return ErrorAttributionDrillDownRestResponse.builder()
        .signal(result.getSignal())
        .eligibility(result.getEligibility())
        .issues(issues)
        .networkEndpoints(nets)
        .build();
  }
}
package org.dreamhorizon.pulseserver.resources.v1.ai;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.v1.ai.models.GetRcaJobResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.rca.RcaReportJobService;

@Slf4j
@Path("/v1/ai-rca")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class GetRcaJobStatus {

  private static final String PROJECT_ID_HEADER = "X-Project-ID";

  private final RcaReportJobService rcaReportJobService;

  @GET
  @Path("/job/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<GetRcaJobResponse>> getJobStatus(
      @PathParam("jobId") String jobId,
      @HeaderParam(PROJECT_ID_HEADER) String projectId) {
    return rcaReportJobService
        .getJobStatus(jobId, projectId)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Read-only status check: returns the cached report ({@code status=COMPLETED}) or an active job
   * ({@code status=PENDING/PROCESSING}) without triggering new job creation.
   * Used by the frontend background poll to detect stale reports or in-progress regeneration.
   * Returns 404 when neither a cached report nor an active job exists.
   */
  @GET
  @Path("/report")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<GetRcaJobResponse>> peekRcaStatus(
      @QueryParam("rcaType") String rcaTypeParam,
      @QueryParam("entityKey") String entityKeyParam,
      @QueryParam("interactionName") String interactionName,
      @QueryParam("date") String dateParam,
      @HeaderParam(PROJECT_ID_HEADER) String projectId) {

    // Support both new (rcaType + entityKey) and legacy (interactionName) parameters
    RcaType type = resolveRcaType(rcaTypeParam);
    String entityKey = resolveEntityKey(entityKeyParam, interactionName);

    if (entityKey == null || entityKey.isBlank()
        || projectId == null || projectId.isBlank()) {
      return Maybe.<GetRcaJobResponse>error(ServiceError.NOT_FOUND.getException())
          .toSingle()
          .to(RestResponse.jaxrsRestHandler());
    }
    LocalDate date = resolveDate(dateParam);
    return rcaReportJobService
        .peekStatus(projectId, type, entityKey, date)
        .switchIfEmpty(Maybe.error(ServiceError.NOT_FOUND.getException()))
        .toSingle()
        .to(RestResponse.jaxrsRestHandler());
  }

  private static RcaType resolveRcaType(String rcaTypeParam) {
    if (rcaTypeParam == null || rcaTypeParam.isBlank()) {
      return RcaType.INTERACTION; // Default type for backward compatibility
    }
    try {
      return RcaType.valueOf(rcaTypeParam.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid RCA type '{}', defaulting to INTERACTION", rcaTypeParam);
      return RcaType.INTERACTION;
    }
  }

  private static String resolveEntityKey(String entityKeyParam, String interactionName) {
    if (entityKeyParam != null && !entityKeyParam.isBlank()) {
      return entityKeyParam;
    }
    // Fall back to legacy interactionName parameter
    return interactionName;
  }

  private static LocalDate resolveDate(final String dateParam) {
    if (dateParam == null || dateParam.isBlank()) {
      return LocalDate.now(ZoneOffset.UTC);
    }
    try {
      return LocalDate.parse(dateParam);
    } catch (DateTimeParseException e) {
      return LocalDate.now(ZoneOffset.UTC);
    }
  }
}

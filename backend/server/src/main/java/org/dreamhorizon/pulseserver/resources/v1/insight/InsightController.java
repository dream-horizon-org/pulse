package org.dreamhorizon.pulseserver.resources.v1.insight;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightExecutionMode;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.v1.insight.models.GetInsightJobResponse;
import org.dreamhorizon.pulseserver.resources.v1.insight.models.TriggerInsightRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.insight.InsightCacheKey;
import org.dreamhorizon.pulseserver.service.insight.InsightJobDispatch;
import org.dreamhorizon.pulseserver.service.insight.InsightJobService;
import org.dreamhorizon.pulseserver.service.insight.InsightProcessor;

@Slf4j
@Path("/v1/insight")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightController {

  private static final String PROJECT_ID_HEADER = "X-Project-ID";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String USER_EMAIL_HEADER = "X-User-Email";

  private final InsightJobService insightJobService;
  private final InsightProcessor insightProcessor;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<GetInsightJobResponse>> triggerInsight(
      @HeaderParam(PROJECT_ID_HEADER) String projectId,
      @HeaderParam(AUTHORIZATION_HEADER) String authorization,
      @HeaderParam(USER_EMAIL_HEADER) String userEmail,
      TriggerInsightRequest request) {

    if (request == null || request.getInsightType() == null
        || request.getInsightType().isBlank()) {
      return Single.<GetInsightJobResponse>error(
          ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException("insightType is required"))
          .to(RestResponse.jaxrsRestHandler());
    }
    if (request.getEntityKey() == null || request.getEntityKey().isBlank()) {
      return Single.<GetInsightJobResponse>error(
          ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException("entityKey is required"))
          .to(RestResponse.jaxrsRestHandler());
    }

    InsightType insightType;
    try {
      insightType = parseInsightType(request.getInsightType());
    } catch (IllegalArgumentException e) {
      return Single.<GetInsightJobResponse>error(
          ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
              "Invalid insightType: " + request.getInsightType()))
          .to(RestResponse.jaxrsRestHandler());
    }

    LocalDate startDate;
    LocalDate endDate;
    try {
      startDate = parseDateParam(request.getStartDate());
      endDate = parseDateParam(request.getEndDate());
    } catch (DateTimeParseException e) {
      return Single.<GetInsightJobResponse>error(
          ServiceError.INCORRECT_OR_MISSING_BODY_PARAMETERS.getCustomException(
              "Invalid date format, expected yyyy-MM-dd"))
          .to(RestResponse.jaxrsRestHandler());
    }

    InsightExecutionMode executionMode =
        (startDate != null && endDate != null)
            ? InsightExecutionMode.DATE_RANGE
            : InsightExecutionMode.REALTIME;

    boolean regenerate = Boolean.TRUE.equals(request.getRegenerate());
    String entityKey = request.getEntityKey().trim();
    final LocalDate resolvedStartDate = startDate;
    final LocalDate resolvedEndDate = endDate;

    InsightCacheKey cacheKey = new InsightCacheKey(
        projectId, insightType, entityKey, executionMode,
        resolvedStartDate, resolvedEndDate, regenerate, null);

    return insightJobService
        .createOrGetJob(cacheKey, userEmail)
        .flatMap(
            dispatch -> {
              if (dispatch.shouldEnqueueWorker()) {
                insightProcessor.enqueueProcess(
                    dispatch.job(), dispatch.regenerate(),
                    authorization, null);
              }
              return buildDispatchResponse(dispatch);
            })
        .to(RestResponse.jaxrsRestHandler(202));
  }

  @GET
  @Path("/job/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<GetInsightJobResponse>> getJobStatus(
      @PathParam("jobId") String jobId,
      @HeaderParam(PROJECT_ID_HEADER) String projectId) {
    return insightJobService
        .getJobStatus(jobId, projectId)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/report")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<GetInsightJobResponse>> peekStatus(
      @QueryParam("insightType") String insightTypeParam,
      @QueryParam("entityKey") String entityKey,
      @QueryParam("startDate") String startDateParam,
      @QueryParam("endDate") String endDateParam,
      @HeaderParam(PROJECT_ID_HEADER) String projectId) {

    InsightType insightType = resolveInsightType(insightTypeParam);
    if (insightType == null || entityKey == null || entityKey.isBlank()
        || projectId == null || projectId.isBlank()) {
      return Maybe.<GetInsightJobResponse>error(ServiceError.NOT_FOUND.getException())
          .toSingle()
          .to(RestResponse.jaxrsRestHandler());
    }

    LocalDate startDate = parseDate(startDateParam);
    LocalDate endDate = parseDate(endDateParam);
    InsightExecutionMode executionMode =
        (startDate != null && endDate != null)
            ? InsightExecutionMode.DATE_RANGE
            : InsightExecutionMode.REALTIME;

    return insightJobService
        .peekStatus(projectId, insightType, entityKey, executionMode, startDate, endDate)
        .switchIfEmpty(Maybe.error(ServiceError.NOT_FOUND.getException()))
        .toSingle()
        .to(RestResponse.jaxrsRestHandler());
  }

  private Single<GetInsightJobResponse> buildDispatchResponse(final InsightJobDispatch dispatch) {
    var job = dispatch.job();
    return Single.just(
        GetInsightJobResponse.builder()
            .jobId(job.jobId())
            .insightType(job.insightType().name())
            .executionMode(job.executionMode().name())
            .status(job.status().name())
            .pollUrl("/v1/insight/job/" + job.jobId())
            .createdAt(job.createdAt())
            .build());
  }

  private static InsightType parseInsightType(final String raw) {
    return InsightType.valueOf(raw.trim().toUpperCase());
  }

  private static LocalDate parseDateParam(final String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return LocalDate.parse(raw.trim());
  }

  private static InsightType resolveInsightType(final String param) {
    if (param == null || param.isBlank()) {
      return null;
    }
    try {
      return InsightType.valueOf(param.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid insightType '{}'", param);
      return null;
    }
  }

  private static LocalDate parseDate(final String dateParam) {
    if (dateParam == null || dateParam.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(dateParam.trim());
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}

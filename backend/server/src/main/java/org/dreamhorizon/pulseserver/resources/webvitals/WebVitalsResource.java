package org.dreamhorizon.pulseserver.resources.webvitals;

import com.google.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.webvitals.WebVitalsService;

@Slf4j
@Path("/v1/web-vitals")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class WebVitalsResource {

  private final WebVitalsService webVitalsService;
  private static final int DEFAULT_BUCKET_MINUTES = 30;
  private static final int MIN_BUCKET_MINUTES = 5;
  private static final int MAX_BUCKET_MINUTES = 1440;

  /**
   * Get web vitals summary for a time range.
   * GET /v1/web-vitals/summary?startTime=<epochMillis|ISO>&endTime=<epochMillis|ISO>&screenName=<optional>
   */
  @GET
  @Path("/summary")
  public CompletionStage<Response<WebVitalsSummaryResponseDto>> getSummary(
      @QueryParam("startTime") String startTimeStr,
      @QueryParam("endTime") String endTimeStr,
      @QueryParam("screenName") String screenName) {

    try {
      ProjectContext.requireProjectId();
    } catch (IllegalStateException e) {
      throw ServiceError.INVALID_PROJECT_ID.getException();
    }

    Instant startTime = WebVitalsTimeParser.parseQueryInstant(startTimeStr, "startTime");
    Instant endTime = WebVitalsTimeParser.parseQueryInstant(endTimeStr, "endTime");

    return webVitalsService
        .getSummary(startTime, endTime, screenName)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Get web vitals trend for a time range.
   * GET /v1/web-vitals/trend?startTime=<epochMillis|ISO>&endTime=<epochMillis|ISO>&vitalName=<name>&bucketMinutes=<optional>&screenName=<optional>
   */
  @GET
  @Path("/trend")
  public CompletionStage<Response<WebVitalsTrendResponseDto>> getTrend(
      @QueryParam("startTime") String startTimeStr,
      @QueryParam("endTime") String endTimeStr,
      @QueryParam("vitalName") String vitalName,
      @QueryParam("bucketMinutes") Integer bucketMinutes,
      @QueryParam("screenName") String screenName) {

    try {
      ProjectContext.requireProjectId();
    } catch (IllegalStateException e) {
      throw ServiceError.INVALID_PROJECT_ID.getException();
    }

    int effectiveBucketMinutes =
        bucketMinutes != null ? bucketMinutes : DEFAULT_BUCKET_MINUTES;

    if (effectiveBucketMinutes < MIN_BUCKET_MINUTES
        || effectiveBucketMinutes > MAX_BUCKET_MINUTES) {
      throw ServiceError.INVALID_BUCKET_MINUTES.getException();
    }

    Instant startTime = WebVitalsTimeParser.parseQueryInstant(startTimeStr, "startTime");
    Instant endTime = WebVitalsTimeParser.parseQueryInstant(endTimeStr, "endTime");

    return webVitalsService
        .getTrend(startTime, endTime, vitalName, effectiveBucketMinutes, screenName)
        .to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Get web vitals breakdown by screen name.
   * GET /v1/web-vitals/by-screen?startTime=<epochMillis|ISO>&endTime=<epochMillis|ISO>&vitalName=<name>
   */
  @GET
  @Path("/by-screen")
  public CompletionStage<Response<WebVitalsByScreenResponseDto>> getByScreen(
      @QueryParam("startTime") String startTimeStr,
      @QueryParam("endTime") String endTimeStr,
      @QueryParam("vitalName") String vitalName) {

    try {
      ProjectContext.requireProjectId();
    } catch (IllegalStateException e) {
      throw ServiceError.INVALID_PROJECT_ID.getException();
    }

    Instant startTime = WebVitalsTimeParser.parseQueryInstant(startTimeStr, "startTime");
    Instant endTime = WebVitalsTimeParser.parseQueryInstant(endTimeStr, "endTime");

    return webVitalsService
        .getByScreen(startTime, endTime, vitalName)
        .to(RestResponse.jaxrsRestHandler());
  }
}

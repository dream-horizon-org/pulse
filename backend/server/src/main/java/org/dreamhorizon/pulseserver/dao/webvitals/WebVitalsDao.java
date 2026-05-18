package org.dreamhorizon.pulseserver.dao.webvitals;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalByScreenRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalSummaryRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalTrendRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class WebVitalsDao {

  private final ClickhouseQueryService clickhouseQueryService;
  private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ISO_INSTANT;

  /**
   * Get web vitals summary for a time range. Routes to global or per-screen query based on
   * screenName.
   *
   * @param startTime start time (inclusive)
   * @param endTime end time (inclusive)
   * @param screenName optional screen name filter; if null/empty, uses global query
   * @return Single containing list of WebVitalSummaryRow
   */
  public Single<List<WebVitalSummaryRow>> getSummary(
      Instant startTime, Instant endTime, String screenName) {
    String projectId = ProjectContext.requireProjectId();
    String query = buildSummaryQuery(startTime, endTime, screenName, projectId);

    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .projectId(projectId)
        .useQueryConditionCache(true)
        .build();

    return clickhouseQueryService
        .executeQueryOrCreateJob(config, WebVitalSummaryRow.class)
        .map(QueryResultResponse::getRows)
        .map(rows -> rows != null ? rows : List.of());
  }

  /**
   * Get web vitals trend for a time range. Routes to global or per-screen query based on
   * screenName.
   *
   * @param startTime start time (inclusive)
   * @param endTime end time (inclusive)
   * @param vitalName name of the vital (LCP, INP, CLS, etc.)
   * @param bucketMinutes interval in minutes for grouping
   * @param screenName optional screen name filter; if null/empty, uses global query
   * @return Single containing list of WebVitalTrendRow
   */
  public Single<List<WebVitalTrendRow>> getTrend(
      Instant startTime, Instant endTime, String vitalName, int bucketMinutes, String screenName) {
    String projectId = ProjectContext.requireProjectId();
    String query = buildTrendQuery(startTime, endTime, vitalName, bucketMinutes, screenName, projectId);

    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .projectId(projectId)
        .useQueryConditionCache(true)
        .build();

    return clickhouseQueryService
        .executeQueryOrCreateJob(config, WebVitalTrendRow.class)
        .map(QueryResultResponse::getRows)
        .map(rows -> rows != null ? rows : List.of());
  }

  /**
   * Get web vitals breakdown by screen name for a specific vital.
   *
   * @param startTime start time (inclusive)
   * @param endTime end time (inclusive)
   * @param vitalName name of the vital (LCP, INP, CLS, etc.)
   * @return Single containing list of WebVitalByScreenRow
   */
  public Single<List<WebVitalByScreenRow>> getByScreen(
      Instant startTime, Instant endTime, String vitalName) {
    String projectId = ProjectContext.requireProjectId();
    String query = buildByScreenQuery(startTime, endTime, vitalName, projectId);

    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .projectId(projectId)
        .useQueryConditionCache(true)
        .build();

    return clickhouseQueryService
        .executeQueryOrCreateJob(config, WebVitalByScreenRow.class)
        .map(QueryResultResponse::getRows)
        .map(rows -> rows != null ? rows : List.of());
  }

  private String buildSummaryQuery(
      Instant startTime, Instant endTime, String screenName, String projectId) {
    String baseQuery = Optional.ofNullable(screenName)
        .filter(s -> !s.isEmpty())
        .map(s -> WebVitalsQueries.GET_WEB_VITALS_SUMMARY_FOR_SCREEN)
        .orElse(WebVitalsQueries.GET_WEB_VITALS_SUMMARY);

    Map<String, Object> substitutionMap = new HashMap<>();
    substitutionMap.put("project_id", escapeChStringLiteral(projectId));
    substitutionMap.put("start_time", TIMESTAMP_FMT.format(startTime));
    substitutionMap.put("end_time", TIMESTAMP_FMT.format(endTime));
    if (screenName != null && !screenName.isEmpty()) {
      substitutionMap.put("screen_name", escapeChStringLiteral(screenName));
    }

    return new StringSubstitutor(substitutionMap).replace(baseQuery);
  }

  private String buildTrendQuery(
      Instant startTime,
      Instant endTime,
      String vitalName,
      int bucketMinutes,
      String screenName,
      String projectId) {
    String baseQuery = Optional.ofNullable(screenName)
        .filter(s -> !s.isEmpty())
        .map(s -> WebVitalsQueries.GET_WEB_VITALS_TREND_FOR_SCREEN)
        .orElse(WebVitalsQueries.GET_WEB_VITALS_TREND);

    Map<String, Object> substitutionMap = new HashMap<>();
    substitutionMap.put("project_id", escapeChStringLiteral(projectId));
    substitutionMap.put("start_time", TIMESTAMP_FMT.format(startTime));
    substitutionMap.put("end_time", TIMESTAMP_FMT.format(endTime));
    substitutionMap.put("vital_name", escapeChStringLiteral(vitalName));
    substitutionMap.put("bucket_minutes", bucketMinutes);
    if (screenName != null && !screenName.isEmpty()) {
      substitutionMap.put("screen_name", escapeChStringLiteral(screenName));
    }

    return new StringSubstitutor(substitutionMap).replace(baseQuery);
  }

  private String buildByScreenQuery(
      Instant startTime, Instant endTime, String vitalName, String projectId) {
    Map<String, Object> substitutionMap = new HashMap<>();
    substitutionMap.put("project_id", escapeChStringLiteral(projectId));
    substitutionMap.put("start_time", TIMESTAMP_FMT.format(startTime));
    substitutionMap.put("end_time", TIMESTAMP_FMT.format(endTime));
    substitutionMap.put("vital_name", escapeChStringLiteral(vitalName));

    return new StringSubstitutor(substitutionMap).replace(WebVitalsQueries.GET_WEB_VITALS_BY_SCREEN);
  }

  private static String escapeChStringLiteral(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}

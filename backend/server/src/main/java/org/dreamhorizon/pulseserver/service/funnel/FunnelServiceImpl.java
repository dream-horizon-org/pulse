package org.dreamhorizon.pulseserver.service.funnel;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.model.JobCreationMode;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelHealthResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelRequest;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelSessionDetail;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelSessionsRequest;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelSessionsResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelStep;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelStepHealth;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelStepResult;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelServiceImpl implements FunnelService {

  private final ClickhouseQueryService clickhouseQueryService;
  private final DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public Single<FunnelResponse> analyzeFunnel(FunnelRequest request) {
    if (request.getGroupBy() != null && !request.getGroupBy().isEmpty()) {
      return executeGroupedFunnel(request);
    }
    return executeSimpleFunnel(request);
  }

  @Override
  public Single<FunnelHealthResponse> getFunnelHealth(FunnelRequest request) {
    String query = buildFunnelHealthQuery(request);
    log.info("Executing funnel health query: {}", query);

    return clickhouseQueryService.executeQueryOrCreateJob(
            QueryConfiguration.newQuery(query)
                .timeoutMs(10000)
                .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
                .tenantId(request.getTenantId())
                .build())
        .map(rawRes -> parseFunnelHealthResults(rawRes.data, request));
  }

  @Override
  public Single<FunnelSessionsResponse> getFunnelSessions(FunnelSessionsRequest request) {
    String query = buildFunnelSessionsQuery(request);
    log.info("Executing funnel sessions query: {}", query);

    return clickhouseQueryService.executeQueryOrCreateJob(
            QueryConfiguration.newQuery(query)
                .timeoutMs(10000)
                .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
                .tenantId(request.getTenantId())
                .build())
        .map(rawRes -> parseFunnelSessionsResults(rawRes.data, request));
  }

  private Single<FunnelResponse> executeSimpleFunnel(FunnelRequest request) {
    String tableName = resolveTableName(request.getSteps());
    String eventField = request.getSteps().isEmpty() ? "N/A" : resolveEventField(request.getSteps().get(0));
    log.info("Funnel config: table={}, eventField={}, mode={}, steps={}, timeRange=[{} -> {}]",
        tableName, eventField, request.getMode(),
        request.getSteps().stream().map(FunnelStep::getEventName).collect(Collectors.toList()),
        request.getTimeRange().getStart(), request.getTimeRange().getEnd());

    String query = buildFunnelQuery(request, null);
    log.info("Executing funnel query: {}", query);

    return clickhouseQueryService.executeQueryOrCreateJob(
            QueryConfiguration.newQuery(query)
                .timeoutMs(5000)
                .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
                .tenantId(request.getTenantId())
                .build())
        .map(rawRes -> parseFunnelResults(rawRes.data, request));
  }

  private Single<FunnelResponse> executeGroupedFunnel(FunnelRequest request) {
    String query = buildGroupedFunnelQuery(request);
    log.info("Executing grouped funnel query: {}", query);

    return clickhouseQueryService.executeQueryOrCreateJob(
            QueryConfiguration.newQuery(query)
                .timeoutMs(10000)
                .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
                .tenantId(request.getTenantId())
                .build())
        .map(rawRes -> parseGroupedFunnelResults(rawRes.data, request));
  }

  private String buildFunnelQuery(FunnelRequest request, String groupByColumn) {
    String identityColumn = getIdentityColumn(request.getMode());
    String timeFilter = buildTimeFilter(request);
    String additionalFilters = buildAdditionalFilters(request.getFilters());
    String eventConditions = buildEventConditions(request.getSteps());
    String tableName = resolveTableName(request.getSteps());

    StringBuilder sb = new StringBuilder();
    sb.append("SELECT level, count() AS cnt FROM (");
    sb.append(" SELECT ").append(identityColumn);
    if (groupByColumn != null) {
      sb.append(", ").append(groupByColumn);
    }
    sb.append(", windowFunnel(").append(request.getWindowSeconds()).append(")(");
    sb.append("toDateTime(Timestamp), ").append(eventConditions);
    sb.append(") AS level");
    sb.append(" FROM ").append(tableName);
    sb.append(" WHERE ").append(timeFilter);
    sb.append(additionalFilters);
    sb.append(" GROUP BY ").append(identityColumn);
    if (groupByColumn != null) {
      sb.append(", ").append(groupByColumn);
    }
    sb.append(")");
    if (groupByColumn != null) {
      sb.append(" GROUP BY ").append(groupByColumn).append(", level");
      sb.append(" ORDER BY ").append(groupByColumn).append(", level");
    } else {
      sb.append(" GROUP BY level ORDER BY level");
    }

    return sb.toString();
  }

  private String buildGroupedFunnelQuery(FunnelRequest request) {
    return buildFunnelQuery(request, request.getGroupBy());
  }

  private String buildEventConditions(List<FunnelStep> steps) {
    return steps.stream()
        .map(step -> {
          String eventField = resolveEventField(step);
          StringBuilder condition = new StringBuilder();
          condition.append(eventField).append(" = '").append(escapeSql(step.getEventName())).append("'");

          if (step.getPulseType() != null && !step.getPulseType().isEmpty()) {
            condition.append(" AND PulseType = '").append(escapeSql(step.getPulseType())).append("'");
          }

          if (!CollectionUtils.isEmpty(step.getStepFilters())) {
            for (FunnelStep.StepFilter filter : step.getStepFilters()) {
              condition.append(" AND ").append(filter.getField()).append(" ")
                  .append(filter.getOperator()).append(" '")
                  .append(escapeSql(String.valueOf(filter.getValue().get(0)))).append("'");
            }
          }

          return condition.toString();
        })
        .collect(Collectors.joining(", "));
  }

  private String resolveEventField(FunnelStep step) {
    String dataType = step.getDataType();
    if (dataType != null && dataType.equalsIgnoreCase("LOGS")) {
      return "Body";
    }
    return "SpanName";
  }

  private String resolveTableName(List<FunnelStep> steps) {
    boolean hasLogs = steps.stream()
        .anyMatch(s -> s.getDataType() != null && s.getDataType().equalsIgnoreCase("LOGS"));
    if (hasLogs) {
      return "otel_logs";
    }
    return "otel_traces";
  }

  private String getIdentityColumn(FunnelRequest.FunnelMode mode) {
    return switch (mode) {
      case SESSIONS -> "SessionId";
      case UNIQUE_USERS -> "UserId";
    };
  }

  private String buildTimeFilter(FunnelRequest request) {
    return buildTimeFilter(request.getTimeRange());
  }

  private String buildAdditionalFilters(List<QueryRequest.Filter> filters) {
    if (CollectionUtils.isEmpty(filters)) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (QueryRequest.Filter filter : filters) {
      sb.append(switch (filter.getOperator()) {
        case LIKE -> String.format(" AND %s like '%s'", filter.getField(), filter.getValue().get(0));
        case IN -> String.format(" AND %s In (%s)", filter.getField(),
            filter.getValue().stream()
                .map(v -> String.format("'%s'", escapeSql(String.valueOf(v))))
                .collect(Collectors.joining(",")));
        case EQ -> String.format(" AND %s = '%s'", filter.getField(),
            escapeSql(String.valueOf(filter.getValue().get(0))));
        case ADDITIONAL -> String.format(" AND (%s)", filter.getValue().get(0));
      });
    }
    return sb.toString();
  }

  private FunnelResponse parseFunnelResults(GetRawUserEventsResponseDto data, FunnelRequest request) {
    Map<Integer, Long> levelCounts = new LinkedHashMap<>();
    List<GetRawUserEventsResponseDto.Row> rows = data.getRows();

    log.info("Funnel query returned {} rows", rows.size());
    for (GetRawUserEventsResponseDto.Row row : rows) {
      int level = Integer.parseInt(row.getRowFields().get(0).getValue().toString());
      long count = Long.parseLong(row.getRowFields().get(1).getValue().toString());
      levelCounts.put(level, count);
    }
    log.info("Funnel level counts: {}", levelCounts);

    int totalSteps = request.getSteps().size();
    List<FunnelStepResult> stepResults = computeStepResults(levelCounts, totalSteps, request);

    long totalEntered = stepResults.isEmpty() ? 0 : stepResults.get(0).getCount();
    long lastStep = stepResults.isEmpty() ? 0 : stepResults.get(stepResults.size() - 1).getCount();
    double overallConversion = totalEntered > 0 ? (double) lastStep / totalEntered * 100.0 : 0.0;

    return FunnelResponse.builder()
        .steps(stepResults)
        .totalEnteredUsers(totalEntered)
        .overallConversionRate(Math.round(overallConversion * 100.0) / 100.0)
        .build();
  }

  private FunnelResponse parseGroupedFunnelResults(GetRawUserEventsResponseDto data, FunnelRequest request) {
    // Rows have: groupByValue, level, cnt
    Map<String, Map<Integer, Long>> groupedLevelCounts = new LinkedHashMap<>();
    List<GetRawUserEventsResponseDto.Row> rows = data.getRows();

    for (GetRawUserEventsResponseDto.Row row : rows) {
      String groupValue = Objects.toString(row.getRowFields().get(0).getValue(), "unknown");
      int level = Integer.parseInt(row.getRowFields().get(1).getValue().toString());
      long count = Long.parseLong(row.getRowFields().get(2).getValue().toString());
      groupedLevelCounts.computeIfAbsent(groupValue, k -> new LinkedHashMap<>()).put(level, count);
    }

    int totalSteps = request.getSteps().size();
    Map<String, List<FunnelStepResult>> groupedResults = new LinkedHashMap<>();
    long totalEntered = 0;
    long totalCompleted = 0;

    for (Map.Entry<String, Map<Integer, Long>> entry : groupedLevelCounts.entrySet()) {
      List<FunnelStepResult> stepResults = computeStepResults(entry.getValue(), totalSteps, request);
      groupedResults.put(entry.getKey(), stepResults);
      if (!stepResults.isEmpty()) {
        totalEntered += stepResults.get(0).getCount();
        totalCompleted += stepResults.get(stepResults.size() - 1).getCount();
      }
    }

    double overallConversion = totalEntered > 0 ? (double) totalCompleted / totalEntered * 100.0 : 0.0;

    return FunnelResponse.builder()
        .groupedResults(groupedResults)
        .totalEnteredUsers(totalEntered)
        .overallConversionRate(Math.round(overallConversion * 100.0) / 100.0)
        .build();
  }

  /**
   * Converts windowFunnel level counts to per-step counts.
   * windowFunnel(w)(ts, cond1, cond2, cond3) returns max consecutive matched level per user.
   * A user at level 3 also passed levels 1 and 2, so we accumulate from top down.
   */
  private List<FunnelStepResult> computeStepResults(Map<Integer, Long> levelCounts, int totalSteps,
      FunnelRequest request) {
    // Users at step K = users with level >= K
    long[] stepCounts = new long[totalSteps + 1];
    for (Map.Entry<Integer, Long> entry : levelCounts.entrySet()) {
      int level = entry.getKey();
      long count = entry.getValue();
      if (level >= 0 && level <= totalSteps) {
        stepCounts[level] = count;
      }
    }

    // Cumulate: users reaching step K = sum of users with level >= K
    long[] reachingStep = new long[totalSteps + 1];
    reachingStep[totalSteps] = stepCounts[totalSteps];
    for (int i = totalSteps - 1; i >= 1; i--) {
      reachingStep[i] = stepCounts[i] + reachingStep[i + 1];
    }

    List<FunnelStepResult> results = new ArrayList<>();
    long firstStepCount = reachingStep.length > 1 ? reachingStep[1] : 0;

    for (int i = 1; i <= totalSteps; i++) {
      long count = reachingStep[i];
      double conversionRate = firstStepCount > 0 ? (double) count / firstStepCount * 100.0 : 0.0;
      double dropoffRate = i == 1 ? 0.0 :
          (reachingStep[i - 1] > 0 ? (1.0 - (double) count / reachingStep[i - 1]) * 100.0 : 0.0);

      results.add(FunnelStepResult.builder()
          .stepName(request.getSteps().get(i - 1).getEventName())
          .count(count)
          .conversionRate(Math.round(conversionRate * 100.0) / 100.0)
          .dropoffRate(Math.round(dropoffRate * 100.0) / 100.0)
          .build());
    }

    return results;
  }

  // ===== Funnel Health: crash/ANR/non-fatal correlation per step =====

  private String buildFunnelHealthQuery(FunnelRequest request) {
    String identityColumn = getIdentityColumn(request.getMode());
    String timeFilter = buildTimeFilter(request);
    String additionalFilters = buildAdditionalFilters(request.getFilters());
    String eventConditions = buildEventConditions(request.getSteps());
    String tableName = resolveTableName(request.getSteps());

    return String.format("""
        WITH funnel_users AS (
            SELECT %s, SessionId,
                windowFunnel(%d)(toDateTime(Timestamp), %s) AS level
            FROM %s
            WHERE %s%s
            GROUP BY %s, SessionId
        )
        SELECT
            f.level,
            count(DISTINCT f.%s) AS total_users,
            count(DISTINCT IF(s.EventName = 'device.crash', f.%s, NULL)) AS crash_users,
            count(DISTINCT IF(s.EventName = 'device.anr', f.%s, NULL)) AS anr_users,
            count(DISTINCT IF(s.EventName = 'non_fatal', f.%s, NULL)) AS non_fatal_users
        FROM funnel_users f
        LEFT JOIN stack_trace_events s
            ON f.SessionId = s.SessionId
            AND %s
        WHERE f.level >= 1
        GROUP BY f.level
        ORDER BY f.level
        """,
        identityColumn, request.getWindowSeconds(), eventConditions,
        tableName, timeFilter, additionalFilters,
        identityColumn,
        identityColumn, identityColumn, identityColumn, identityColumn,
        timeFilter.replace("Timestamp", "s.Timestamp"));
  }

  private FunnelHealthResponse parseFunnelHealthResults(GetRawUserEventsResponseDto data,
      FunnelRequest request) {
    // Rows: level, total_users, crash_users, anr_users, non_fatal_users
    int totalSteps = request.getSteps().size();
    Map<Integer, long[]> levelData = new LinkedHashMap<>();

    for (GetRawUserEventsResponseDto.Row row : data.getRows()) {
      List<GetRawUserEventsResponseDto.RowField> fields = row.getRowFields();
      int level = Integer.parseInt(fields.get(0).getValue().toString());
      long totalUsers = Long.parseLong(fields.get(1).getValue().toString());
      long crashUsers = Long.parseLong(fields.get(2).getValue().toString());
      long anrUsers = Long.parseLong(fields.get(3).getValue().toString());
      long nonFatalUsers = Long.parseLong(fields.get(4).getValue().toString());
      levelData.put(level, new long[]{totalUsers, crashUsers, anrUsers, nonFatalUsers});
    }

    // Accumulate from top (level N users also reached levels 1..N-1)
    long[][] stepData = new long[totalSteps + 1][4];
    for (Map.Entry<Integer, long[]> entry : levelData.entrySet()) {
      int level = entry.getKey();
      if (level >= 1 && level <= totalSteps) {
        stepData[level] = entry.getValue();
      }
    }

    // Users reaching step K = sum of users with level >= K
    long[][] reachingStep = new long[totalSteps + 1][4];
    reachingStep[totalSteps] = stepData[totalSteps].clone();
    for (int i = totalSteps - 1; i >= 1; i--) {
      for (int j = 0; j < 4; j++) {
        reachingStep[i][j] = stepData[i][j] + reachingStep[i + 1][j];
      }
    }

    List<FunnelStepHealth> steps = new ArrayList<>();
    long totalCrash = 0, totalAnr = 0, totalNonFatal = 0;

    for (int i = 1; i <= totalSteps; i++) {
      long total = reachingStep[i][0];
      long crash = reachingStep[i][1];
      long anr = reachingStep[i][2];
      long nonFatal = reachingStep[i][3];

      totalCrash += crash;
      totalAnr += anr;
      totalNonFatal += nonFatal;

      steps.add(FunnelStepHealth.builder()
          .stepLevel(i)
          .stepName(request.getSteps().get(i - 1).getEventName())
          .totalUsers(total)
          .crashUsers(crash)
          .anrUsers(anr)
          .nonFatalUsers(nonFatal)
          .crashRate(total > 0 ? Math.round((double) crash / total * 10000.0) / 100.0 : 0.0)
          .anrRate(total > 0 ? Math.round((double) anr / total * 10000.0) / 100.0 : 0.0)
          .nonFatalRate(total > 0 ? Math.round((double) nonFatal / total * 10000.0) / 100.0 : 0.0)
          .build());
    }

    return FunnelHealthResponse.builder()
        .steps(steps)
        .totalCrashUsers(totalCrash)
        .totalAnrUsers(totalAnr)
        .totalNonFatalUsers(totalNonFatal)
        .build();
  }

  // ===== Funnel Sessions: drill-down into affected sessions =====

  private String buildFunnelSessionsQuery(FunnelSessionsRequest request) {
    String identityColumn = request.getMode() == FunnelRequest.FunnelMode.SESSIONS
        ? "SessionId" : "UserId";
    String timeFilter = buildTimeFilter(request.getTimeRange());
    String additionalFilters = buildAdditionalFilters(request.getFilters());
    String eventConditions = buildEventConditions(request.getSteps());
    String tableName = resolveTableName(request.getSteps());

    String issueFilter = switch (request.getIssueType().toUpperCase()) {
      case "CRASH" -> "AND s.EventName = 'device.crash'";
      case "ANR" -> "AND s.EventName = 'device.anr'";
      case "NON_FATAL" -> "AND s.EventName = 'non_fatal'";
      default -> "AND s.EventName IN ('device.crash', 'device.anr', 'non_fatal')";
    };

    return String.format("""
        WITH funnel_sessions AS (
            SELECT %s, SessionId,
                windowFunnel(%d)(toDateTime(Timestamp), %s) AS level
            FROM %s
            WHERE %s%s
            GROUP BY %s, SessionId
            HAVING level >= %d
        )
        SELECT
            f.SessionId,
            f.%s AS UserId,
            s.EventName,
            s.ExceptionType,
            s.ExceptionMessage,
            s.Title,
            s.ScreenName,
            toString(s.Timestamp) AS crash_time,
            s.GroupId,
            s.Platform,
            s.AppVersion,
            s.DeviceModel
        FROM funnel_sessions f
        INNER JOIN stack_trace_events s
            ON f.SessionId = s.SessionId
            AND %s
            %s
        ORDER BY s.Timestamp DESC
        LIMIT %d
        """,
        identityColumn, request.getWindowSeconds(), eventConditions,
        tableName, timeFilter, additionalFilters,
        identityColumn,
        request.getStepLevel(),
        identityColumn,
        timeFilter.replace("Timestamp", "s.Timestamp"),
        issueFilter,
        Objects.requireNonNullElse(request.getLimit(), 100));
  }

  private String buildTimeFilter(QueryRequest.TimeRange timeRange) {
    String start = ZonedDateTime.parse(timeRange.getStart()).format(outputFormat);
    String end = ZonedDateTime.parse(timeRange.getEnd()).format(outputFormat);
    return String.format(
        "Timestamp >= toDateTime64('%s',9,'UTC') AND Timestamp <= toDateTime64('%s',9,'UTC')",
        start, end);
  }

  private FunnelSessionsResponse parseFunnelSessionsResults(GetRawUserEventsResponseDto data,
      FunnelSessionsRequest request) {
    List<FunnelSessionDetail> sessions = new ArrayList<>();

    for (GetRawUserEventsResponseDto.Row row : data.getRows()) {
      List<GetRawUserEventsResponseDto.RowField> f = row.getRowFields();
      sessions.add(FunnelSessionDetail.builder()
          .sessionId(safeStr(f.get(0)))
          .userId(safeStr(f.get(1)))
          .eventName(safeStr(f.get(2)))
          .exceptionType(safeStr(f.get(3)))
          .exceptionMessage(safeStr(f.get(4)))
          .title(safeStr(f.get(5)))
          .screenName(safeStr(f.get(6)))
          .timestamp(safeStr(f.get(7)))
          .groupId(safeStr(f.get(8)))
          .platform(safeStr(f.get(9)))
          .appVersion(safeStr(f.get(10)))
          .deviceModel(safeStr(f.get(11)))
          .build());
    }

    String stepName = request.getStepLevel() <= request.getSteps().size()
        ? request.getSteps().get(request.getStepLevel() - 1).getEventName()
        : "Step " + request.getStepLevel();

    return FunnelSessionsResponse.builder()
        .stepLevel(request.getStepLevel())
        .stepName(stepName)
        .totalAffectedSessions(sessions.size())
        .sessions(sessions)
        .build();
  }

  private String safeStr(GetRawUserEventsResponseDto.RowField field) {
    return field.getValue() == null ? "" : field.getValue().toString();
  }

  private String escapeSql(String value) {
    if (value == null) return "";
    return value.replace("'", "\\'");
  }
}

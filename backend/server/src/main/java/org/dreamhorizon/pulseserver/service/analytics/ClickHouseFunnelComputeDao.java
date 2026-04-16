package org.dreamhorizon.pulseserver.service.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDefinitionStep;

/**
 * Builds ClickHouse INSERT…WITH SQL for funnel computation.
 *
 * <p>Reads from {@code otel.otel_logs} with {@code LogAttributes['pulse.type'] = 'custom_event'}.
 *
 * <p>{@code windowFunnel}'s first argument must be {@code DateTime} (not {@code DateTime64}); we use
 * {@code toDateTime(Timestamp)} in the {@code raw} CTE.
 */
@Slf4j
public final class ClickHouseFunnelComputeDao {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ClickHouseFunnelComputeDao() {}

  /**
   * Builds the INSERT SQL for a single funnel definition. Used on the on-save path (both AUTO and
   * ONCE modes).
   */
  public static String buildInsertSql(FunnelDefinitionRow def) {
    List<FunnelDefinitionStep> steps = deserializeSteps(def.getStepsJson());
    List<FunnelAttributeFilter> filters = deserializeFilters(def.getFiltersJson());

    String globalFilterClauses = filters.stream()
        .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
        .collect(Collectors.joining("\n      "));

    String groupKey = ClickhouseAnalyticsQueryUtils.resolveGroupKey(def.getMode());
    String startExpr = ClickhouseAnalyticsQueryUtils.resolveStartExpr(
        def.getFunnelType(), def.getDateRangeDays(), def.getStartTime());
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(def.getFunnelType(), def.getEndTime());

    String windowFunnelArgs = steps.stream()
        .map(s -> "EventName = '" + escape(s.getEventName()) + "'")
        .collect(Collectors.joining(",\n          "));

    String stepNamesArray = "[" + steps.stream()
        .map(s -> "'" + escape(s.getEventName()) + "'")
        .collect(Collectors.joining(", ")) + "]";

    int stepCount = steps.size();
    String projectId = def.getProjectId();
    long funnelId = def.getId();
    long windowSeconds = def.getWindowSeconds();

    return """
        INSERT INTO otel.funnel_results
          (FunnelId, ProjectId, RunTime, StepIndex, StepName, UserCount, ConversionPct, MedianStepSeconds)
        WITH
          raw AS (
            SELECT %s AS uid, toDateTime(Timestamp) AS FunnelTs, Body AS EventName
            FROM otel.otel_logs
            WHERE ResourceAttributes['project.id'] = '%s'
              AND LogAttributes['pulse.type'] = 'custom_event'
              AND Timestamp BETWEEN %s AND %s
              %s
          ),
          levels AS (
            SELECT uid,
              windowFunnel(%d)(FunnelTs,
                %s
              ) AS lvl
            FROM raw GROUP BY uid
          ),
          step_counts AS (
            SELECT step_num.number AS number,
              countIf(lvl >= step_num.number + 1) AS UserCount
            FROM levels
            CROSS JOIN (SELECT arrayJoin(range(%d)) AS number) AS step_num
            GROUP BY step_num.number
          ),
          step_rows AS (
            SELECT drv.number + 1 AS StepIndex,
                   %s[drv.number + 1] AS StepName,
                   ifNull(sc.UserCount, 0) AS UserCount
            FROM (SELECT arrayJoin(range(%d)) AS number) AS drv
            LEFT JOIN step_counts sc ON drv.number = sc.number
          )
        SELECT %d, '%s', now(), StepIndex, StepName,
               UserCount,
               UserCount * 100.0 / greatest((SELECT countIf(lvl >= 1) FROM levels), 1),
               NULL
        FROM step_rows
        """.formatted(
        groupKey, projectId, startExpr, endExpr, globalFilterClauses,
        windowSeconds, windowFunnelArgs,
        stepCount, stepNamesArray, stepCount,
        funnelId, projectId);
  }

  /**
   * Builds a batch INSERT SQL covering all AUTO funnels for a single project in one query.
   * Used by the batch cron path.
   *
   * <p>Scans {@code otel.otel_logs} once via a shared {@code raw} CTE; each funnel gets its own
   * {@code windowFunnel()} CTE reading from {@code raw}.
   */
  public static String buildBatchInsertSql(List<FunnelDefinitionRow> defs) {
    if (defs == null || defs.isEmpty()) {
      return "";
    }

    String projectId = defs.get(0).getProjectId();
    int maxDays = defs.stream().mapToInt(FunnelDefinitionRow::getDateRangeDays).max().orElse(30);

    StringBuilder sb = new StringBuilder();
    sb.append("""
        INSERT INTO otel.funnel_results
          (FunnelId, ProjectId, RunTime, StepIndex, StepName, UserCount, ConversionPct, MedianStepSeconds)
        WITH
          raw AS (
            SELECT LogAttributes['user.id'] AS UserId,
                   LogAttributes['session.id'] AS SessionId,
                   Timestamp,
                   toDateTime(Timestamp) AS FunnelTs,
                   Body AS EventName
            FROM otel.otel_logs
            WHERE ResourceAttributes['project.id'] = '%s'
              AND LogAttributes['pulse.type'] = 'custom_event'
              AND Timestamp >= now() - INTERVAL %d DAY
          ),
        """.formatted(projectId, maxDays));

    List<String> cteNames = new ArrayList<>();
    for (int i = 0; i < defs.size(); i++) {
      FunnelDefinitionRow def = defs.get(i);
      List<FunnelDefinitionStep> steps = deserializeSteps(def.getStepsJson());
      String groupKey = "SESSIONS".equalsIgnoreCase(def.getMode()) ? "SessionId" : "UserId";
      String windowFunnelArgs = steps.stream()
          .map(s -> "EventName = '" + escape(s.getEventName()) + "'")
          .collect(Collectors.joining(", "));

      String cteName = "lvl_f" + i;
      cteNames.add(cteName);
      String tighterFilter = def.getDateRangeDays() < maxDays
          ? "WHERE Timestamp >= now() - INTERVAL " + def.getDateRangeDays() + " DAY"
          : "";

      sb.append("  ").append(cteName).append(" AS (\n");
      sb.append("    SELECT ").append(groupKey).append(" AS uid,\n");
      sb.append("      windowFunnel(").append(def.getWindowSeconds()).append(")(FunnelTs, ")
          .append(windowFunnelArgs).append(") AS lvl\n");
      sb.append("    FROM raw ").append(tighterFilter).append(" GROUP BY uid\n");
      sb.append("  )");
      if (i < defs.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }

    // Build the SELECT UNION ALL block
    for (int i = 0; i < defs.size(); i++) {
      FunnelDefinitionRow def = defs.get(i);
      List<FunnelDefinitionStep> steps = deserializeSteps(def.getStepsJson());
      String stepNamesArray = "[" + steps.stream()
          .map(s -> "'" + escape(s.getEventName()) + "'")
          .collect(Collectors.joining(", ")) + "]";
      int stepCount = steps.size();
      String cteName = cteNames.get(i);

      if (i == 0) {
        sb.append("SELECT * FROM (\n");
      }
      sb.append("  SELECT ").append(def.getId()).append(", '").append(projectId)
          .append("', now(), drv.number + 1, ").append(stepNamesArray)
          .append("[drv.number + 1], ")
          .append("ifNull(aggs.UserCount, 0), ")
          .append("ifNull(aggs.UserCount, 0) * 100.0 / ")
          .append("greatest((SELECT countIf(lvl >= 1) FROM ").append(cteName).append("), 1), NULL\n");
      sb.append("  FROM (SELECT arrayJoin(range(").append(stepCount).append(")) AS number) AS drv\n");
      sb.append("  LEFT JOIN (\n");
      sb.append("    SELECT step_num.number AS number,\n");
      sb.append("      countIf(lvl >= step_num.number + 1) AS UserCount\n");
      sb.append("    FROM ").append(cteName).append("\n");
      sb.append("    CROSS JOIN (SELECT arrayJoin(range(").append(stepCount)
          .append(")) AS number) AS step_num\n");
      sb.append("    GROUP BY step_num.number\n");
      sb.append("  ) AS aggs ON drv.number = aggs.number\n");
      if (i < defs.size() - 1) {
        sb.append("  UNION ALL\n");
      }
    }
    sb.append(")");

    return sb.toString();
  }

  private static List<FunnelDefinitionStep> deserializeSteps(String stepsJson) {
    if (stepsJson == null || stepsJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return MAPPER.readValue(stepsJson, new TypeReference<List<FunnelDefinitionStep>>() {});
    } catch (Exception e) {
      log.error("Failed to deserialize funnel steps JSON: {}", stepsJson, e);
      return Collections.emptyList();
    }
  }

  private static List<FunnelAttributeFilter> deserializeFilters(String filtersJson) {
    if (filtersJson == null || filtersJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return MAPPER.readValue(filtersJson, new TypeReference<List<FunnelAttributeFilter>>() {});
    } catch (Exception e) {
      log.error("Failed to deserialize funnel filters JSON: {}", filtersJson, e);
      return Collections.emptyList();
    }
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("'", "\\'");
  }
}

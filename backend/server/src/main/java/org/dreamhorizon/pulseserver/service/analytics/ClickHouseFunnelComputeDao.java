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

    String groupKey = ClickhouseAnalyticsQueryUtils.resolveGroupKey(def.getFunnelType());
    String startExpr = ClickhouseAnalyticsQueryUtils.resolveStartExpr(
        def.getMode(), def.getDateRangeDays(), def.getStartTime());
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(def.getMode(), def.getEndTime());

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
            SELECT %s AS uid, Timestamp, Body AS EventName
            FROM otel.otel_logs
            WHERE ResourceAttributes['project.id'] = '%s'
              AND LogAttributes['pulse.type'] = 'custom_event'
              AND Timestamp BETWEEN %s AND %s
              %s
          ),
          levels AS (
            SELECT uid,
              windowFunnel(%d)(Timestamp,
                %s
              ) AS lvl
            FROM raw GROUP BY uid
          ),
          step0_count AS (SELECT countIf(lvl >= 1) AS cnt FROM levels),
          step_rows AS (
            SELECT number + 1 AS StepIndex,
                   %s[number + 1] AS StepName,
                   countIf(levels.lvl >= number + 1) AS UserCount
            FROM levels
            ARRAY JOIN range(%d) AS number
            GROUP BY number
          )
        SELECT %d, '%s', now(), StepIndex, StepName,
               UserCount,
               UserCount * 100.0 / (SELECT cnt FROM step0_count),
               NULL
        FROM step_rows
        """.formatted(
        groupKey, projectId, startExpr, endExpr, globalFilterClauses,
        windowSeconds, windowFunnelArgs,
        stepNamesArray, stepCount,
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
      String groupKey = "SESSIONS".equalsIgnoreCase(def.getFunnelType()) ? "SessionId" : "UserId";
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
      sb.append("      windowFunnel(").append(def.getWindowSeconds()).append(")(Timestamp, ")
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
          .append("', now(), number + 1, ").append(stepNamesArray)
          .append("[number + 1], countIf(lvl >= number + 1), ")
          .append("countIf(lvl >= number + 1) * 100.0 / nullIf(countIf(lvl >= 1), 0), NULL\n");
      sb.append("  FROM ").append(cteName).append("\n");
      sb.append("  ARRAY JOIN range(").append(stepCount).append(") AS number\n");
      sb.append("  GROUP BY number\n");
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

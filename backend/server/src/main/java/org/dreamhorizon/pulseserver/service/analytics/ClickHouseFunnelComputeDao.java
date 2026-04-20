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
 * Custom event names are read from the {@code Body} column (not {@code EventName}).
 *
 * <p>Two SQL builders are provided:
 * <ul>
 *   <li>{@link #buildInsertSqlChain(FunnelDefinitionRow)} — default: greedy-forward chain that
 *       enumerates all step-0 attempts per user, walks forward step-by-step, picks the deepest
 *       attempt per user (tie-broken by earliest {@code t0}), and derives both
 *       {@code UserCount} and {@code MedianStepSeconds} from that winning attempt. Respects the
 *       single "deepest completed journey" per user for both counts and medians.</li>
 *   <li>{@link #buildInsertSql(FunnelDefinitionRow)} and
 *       {@link #buildBatchInsertSql(java.util.List)} — legacy {@code windowFunnel}-based
 *       builders retained as a backup. These emit {@code NULL} for {@code MedianStepSeconds}.</li>
 * </ul>
 *
 * <p>{@code windowFunnel}'s first argument must be {@code DateTime} (not {@code DateTime64}); we use
 * {@code toDateTime(Timestamp)} in the scanning CTE for both builders.
 */
@Slf4j
public final class ClickHouseFunnelComputeDao {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ClickHouseFunnelComputeDao() {}

  /**
   * Builds the chain-based INSERT SQL for a single funnel. This is the default builder.
   *
   * <p>Algorithm (per user):
   * <ol>
   *   <li>Enumerate all step-0 events as candidate attempt start points ({@code t0}).</li>
   *   <li>For each subsequent step {@code k}, find the earliest event matching step {@code k}
   *       that is {@code >= t_{k-1}} and {@code <= t0 + window} (window is anchored at
   *       {@code t0}). NULL propagates when no such event exists.</li>
   *   <li>Score each attempt by depth (how many steps were reached).</li>
   *   <li>Select the winning attempt per user with {@code argMax(..., (depth, -t0))}
   *       (deepest wins; earliest {@code t0} breaks ties).</li>
   *   <li>Per step, compute {@code UserCount = countIf(winning_depth >= step)} and
   *       {@code MedianStepSeconds = quantileTDigestIf(0.5)(dateDiff(...))} on the winning
   *       chain timestamps.</li>
   * </ol>
   *
   * <p>Uses the materialized {@code UserId} / {@code SessionId} columns (which include the
   * canonical {@code user.id → app.installation.id} fallback) for entity grouping.
   *
   * @param def funnel definition; must have at least one step
   * @return the INSERT SQL, or an empty string if the funnel has no steps
   */
  public static String buildInsertSqlChain(FunnelDefinitionRow def) {
    List<FunnelDefinitionStep> steps = deserializeSteps(def.getStepsJson());
    if (steps.isEmpty()) {
      return "";
    }
    List<FunnelAttributeFilter> filters = deserializeFilters(def.getFiltersJson());

    int stepCount = steps.size();
    long windowSeconds = def.getWindowSeconds();
    long funnelId = def.getId();
    String projectId = def.getProjectId();

    String groupKey = ClickhouseAnalyticsQueryUtils.resolveMaterializedGroupKey(def.getMode());
    String startExpr = ClickhouseAnalyticsQueryUtils.resolveStartExpr(
        def.getFunnelType(), def.getDateRangeDays(), def.getStartTime());
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(
        def.getFunnelType(), def.getEndTime());

    String bodyInClause = steps.stream()
        .map(s -> "'" + escape(s.getEventName()) + "'")
        .collect(Collectors.joining(", "));

    String additionalFilters = filters.stream()
        .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
        .collect(Collectors.joining("\n      "));

    StringBuilder sql = new StringBuilder(2048);
    sql.append("INSERT INTO otel.funnel_results\n")
        .append("  (FunnelId, ProjectId, RunTime, StepIndex, StepName, UserCount, ConversionPct, MedianStepSeconds)\n")
        .append("WITH\n");

    // step_events: shared filtered scan, narrowed to this funnel's step event names.
    sql.append("  step_events AS (\n")
        .append("    SELECT ").append(groupKey).append(" AS uid,\n")
        .append("           toDateTime(Timestamp) AS FunnelTs,\n")
        .append("           Body\n")
        .append("    FROM otel.otel_logs\n")
        .append("    WHERE ResourceAttributes['project.id'] = '").append(projectId).append("'\n")
        .append("      AND LogAttributes['pulse.type'] = 'custom_event'\n")
        .append("      AND Timestamp BETWEEN ").append(startExpr).append(" AND ").append(endExpr).append("\n")
        .append("      AND Body IN (").append(bodyInClause).append(")\n");
    if (!additionalFilters.isBlank()) {
      sql.append("      ").append(additionalFilters).append("\n");
    }
    sql.append("  ),\n");

    // attempts: one row per (user, step-0 event). Each is a candidate starting point for a
    // greedy forward walk. Multi-attempt enumeration matches windowFunnel's internal behavior.
    sql.append("  attempts AS (\n")
        .append("    SELECT uid, FunnelTs AS t0\n")
        .append("    FROM step_events\n")
        .append("    WHERE Body = '").append(escape(steps.get(0).getEventName())).append("'\n")
        .append("  )");

    // s1..s(N-1): chain forward. Each CTE adds t_i via LEFT JOIN + min() on the next step's
    // events, constrained to [t_{i-1}, t0 + window]. NULL propagates cleanly when a user
    // didn't reach the prior step (NULL comparisons are falsy → no match → min() returns NULL).
    for (int i = 1; i < stepCount; i++) {
      String prevCte = i == 1 ? "attempts" : ("s" + (i - 1));
      String prevAlias = i == 1 ? "a" : ("s" + (i - 1));
      String stepName = escape(steps.get(i).getEventName());

      sql.append(",\n  s").append(i).append(" AS (\n")
          .append("    SELECT ").append(prevAlias).append(".uid, ").append(prevAlias).append(".t0");
      for (int j = 1; j < i; j++) {
        sql.append(", ").append(prevAlias).append(".t").append(j);
      }
      sql.append(",\n           min(e.FunnelTs) AS t").append(i).append("\n")
          .append("    FROM ").append(prevCte).append(" AS ").append(prevAlias).append("\n")
          .append("    LEFT JOIN step_events e\n")
          .append("      ON e.uid = ").append(prevAlias).append(".uid\n")
          .append("     AND e.Body = '").append(stepName).append("'\n")
          .append("     AND e.FunnelTs >= ").append(prevAlias).append(".t").append(i - 1).append("\n")
          .append("     AND e.FunnelTs <= ").append(prevAlias).append(".t0 + INTERVAL ")
          .append(windowSeconds).append(" SECOND\n")
          .append("    GROUP BY ").append(prevAlias).append(".uid, ").append(prevAlias).append(".t0");
      for (int j = 1; j < i; j++) {
        sql.append(", ").append(prevAlias).append(".t").append(j);
      }
      sql.append("\n  )");
    }

    // scored: compute depth for each attempt. depth = k means the user reached step k
    // (1-indexed) on this attempt.
    String lastChainCte = stepCount == 1 ? "attempts" : ("s" + (stepCount - 1));
    sql.append(",\n  scored AS (\n")
        .append("    SELECT uid, t0");
    for (int i = 1; i < stepCount; i++) {
      sql.append(", t").append(i);
    }
    if (stepCount == 1) {
      sql.append(", 1 AS depth\n");
    } else {
      sql.append(",\n           multiIf(\n");
      for (int i = stepCount - 1; i >= 1; i--) {
        sql.append("             t").append(i).append(" IS NOT NULL, ").append(i + 1).append(",\n");
      }
      sql.append("             1\n")
          .append("           ) AS depth\n");
    }
    sql.append("    FROM ").append(lastChainCte).append("\n")
        .append("  ),\n");

    // winners: per user, pick the attempt that reached the deepest depth; ties broken by
    // earliest t0 (encoded as -toInt64(toUnixTimestamp(t0)) so larger = earlier in the
    // lexicographic tuple comparison).
    sql.append("  winners AS (\n")
        .append("    SELECT uid,\n")
        .append("      argMax(tuple(t0");
    for (int i = 1; i < stepCount; i++) {
      sql.append(", t").append(i);
    }
    sql.append("), tuple(depth, -toInt64(toUnixTimestamp(t0)))) AS chain,\n")
        .append("      max(depth) AS winning_depth\n")
        .append("    FROM scored\n")
        .append("    GROUP BY uid\n")
        .append("  )\n");

    // Final SELECT: one UNION ALL branch per step, emitting UserCount + MedianStepSeconds.
    // Step 1's median is always NULL (no previous step). Steps 2..N compute the median
    // delta from the previous step on the winning chain, only over users who reached the step.
    for (int k = 1; k <= stepCount; k++) {
      if (k > 1) {
        sql.append("UNION ALL\n");
      }
      String stepName = escape(steps.get(k - 1).getEventName());
      sql.append("SELECT toUInt64(").append(funnelId).append("), '").append(projectId)
          .append("', now64(3), toUInt8(").append(k - 1).append("), '").append(stepName).append("',\n")
          .append("       countIf(winning_depth >= ").append(k).append("),\n")
          .append("       countIf(winning_depth >= ").append(k)
          .append(") * 100.0 / greatest(count(), 1),\n");
      if (k == 1) {
        sql.append("       CAST(NULL AS Nullable(Int64))\n");
      } else {
        // dateDiff returns NULL when either operand is NULL (winning_depth < k). quantileTDigest
        // ignores NULLs; returns NaN if all inputs are NULL. accurateCastOrNull maps NaN to
        // NULL cleanly, giving us the intended "no data" signal for empty steps.
        sql.append("       accurateCastOrNull(round(quantileTDigest(0.5)(\n")
            .append("         dateDiff('second', chain.").append(k - 1).append(", chain.").append(k).append(")\n")
            .append("       )), 'Int64')\n");
      }
      sql.append("FROM winners\n");
    }

    return sql.toString();
  }

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
        .map(s -> "Body = '" + escape(s.getEventName()) + "'")
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
            SELECT %s AS uid, toDateTime(Timestamp) AS FunnelTs, Body
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
                   Body
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
          .map(s -> "Body = '" + escape(s.getEventName()) + "'")
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

package org.dreamhorizon.pulseserver.service.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * <p>Reads from {@code otel.otel_logs} with {@code PulseType = 'custom_event'}
 * (materialized from {@code LogAttributes['pulse.type']}).
 * Custom event names are read from the {@code EventName} column.
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
  private static final String STEP_ORDER_UNORDERED = "UNORDERED";
  private static final DateTimeFormatter RUN_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

  private ClickHouseFunnelComputeDao() {}

  /**
   * A single {@code RunTime} literal used to stamp every row of one funnel INSERT. All
   * UNION ALL branches must share the same value so the "latest run" query
   * ({@code WHERE RunTime = (SELECT max(RunTime) ...)}) returns every step. Using
   * {@code now64(3)} per branch yields slightly different millisecond values and causes
   * {@code max(RunTime)} to match only the last-evaluated branch.
   */
  static String runTimeLiteral() {
    return "toDateTime64('" + RUN_TIME_FMT.format(Instant.now()) + "', 3, 'UTC')";
  }

  /**
   * Exposes a fresh {@code RunTime} literal so callers can share the SAME run stamp across the
   * {@code funnel_results} insert and the accompanying {@code funnel_session_state} /
   * {@code funnel_user_state} inserts. All three tables must agree on {@code RunTime} so the
   * drop-off DAO can join them by {@code MAX(RunTime)} per funnel.
   */
  public static String newRunTimeLiteral() {
    return runTimeLiteral();
  }

  /**
   * Builds INSERT SQL using the funnel's configured step-order semantics.
   *
   * <p>{@code stepOrderType == UNORDERED} uses {@link #buildInsertSqlUnordered(FunnelDefinitionRow)};
   * all other values use {@link #buildInsertSqlChain(FunnelDefinitionRow)}.
   */
  public static String buildInsertSqlForDefinition(FunnelDefinitionRow def) {
    return buildInsertSqlForDefinition(def, runTimeLiteral());
  }

  /**
   * Overload that lets the caller pin the {@code RunTime} literal so the accompanying bridge
   * inserts can share it.
   */
  public static String buildInsertSqlForDefinition(FunnelDefinitionRow def, String runTime) {
    if (isUnorderedFunnel(def)) {
      return buildInsertSqlUnordered(def, runTime);
    }
    return buildInsertSqlChain(def, runTime);
  }

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
   *       {@code MedianStepSeconds} via {@code quantileExactIf(0.5)} on per-step
   *       {@code dateDiff} over the winning chain.</li>
   * </ol>
   *
   * <p>Groups by materialized {@code UserId} / {@code SessionId} on {@code otel.otel_logs}
   * (see ingestion DDL: {@code user.id} with {@code app.installation.id} fallback).
   *
   * @param def funnel definition; must have at least one step
   * @return the INSERT SQL, or an empty string if the funnel has no steps
   */
  public static String buildInsertSqlChain(FunnelDefinitionRow def) {
    return buildInsertSqlChain(def, runTimeLiteral());
  }

  /** Overload for a caller-supplied {@code RunTime} literal; see {@link #newRunTimeLiteral()}. */
  public static String buildInsertSqlChain(FunnelDefinitionRow def, String runTime) {
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
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(def.getFunnelType(), def.getEndTime());

    String eventNameInClause = steps.stream()
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
        .append("           EventName\n")
        .append("    FROM otel.otel_logs\n")
        .append("    WHERE ProjectId = '").append(projectId).append("'\n")
        .append("      AND PulseType = 'custom_event'\n")
        .append("      AND Timestamp BETWEEN ").append(startExpr).append(" AND ").append(endExpr).append("\n")
        .append("      AND EventName IN (").append(eventNameInClause).append(")\n");
    if (!additionalFilters.isBlank()) {
      sql.append("      ").append(additionalFilters).append("\n");
    }
    sql.append("  ),\n");

    // attempts: one row per (user, step-0 event). Each is a candidate starting point for a
    // greedy forward walk. Multi-attempt enumeration matches windowFunnel's internal behavior.
    sql.append("  attempts AS (\n")
        .append("    SELECT uid, FunnelTs AS t0\n")
        .append("    FROM step_events\n")
        .append("    WHERE EventName = '").append(escape(steps.get(0).getEventName())).append("'\n")
        .append("  )");

    // s1..s(N-1): chain forward. ClickHouse rejects non-equi predicates in JOIN ON unless
    // allow_experimental_join_condition is set; equi-join on (uid, EventName) only and apply the
    // funnel window [t_{i-1}, t0 + W] inside minOrNullIf.
    //
    // Use minOrNullIf (not minIf): for non-Nullable DateTime, minIf with no matching rows
    // returns default 1970-01-01, which is still IS NOT NULL and breaks depth (every user looks
    // like they completed all steps) and poisons median dateDiff with huge negatives.
    for (int i = 1; i < stepCount; i++) {
      String prevCte = i == 1 ? "attempts" : ("s" + (i - 1));
      String prevAlias = i == 1 ? "a" : ("s" + (i - 1));
      String stepName = escape(steps.get(i).getEventName());

      sql.append(",\n  s").append(i).append(" AS (\n")
          .append("    SELECT ").append(prevAlias).append(".uid, ").append(prevAlias).append(".t0");
      for (int j = 1; j < i; j++) {
        sql.append(", ").append(prevAlias).append(".t").append(j);
      }
      sql.append(",\n           minOrNullIf(e.FunnelTs, e.FunnelTs >= ").append(prevAlias).append(".t")
          .append(i - 1).append(" AND e.FunnelTs <= ").append(prevAlias).append(".t0 + INTERVAL ")
          .append(windowSeconds).append(" SECOND) AS t").append(i).append("\n")
          .append("    FROM ").append(prevCte).append(" AS ").append(prevAlias).append("\n")
          .append("    LEFT JOIN step_events e\n")
          .append("      ON e.uid = ").append(prevAlias).append(".uid\n")
          .append("     AND e.EventName = '").append(stepName).append("'\n")
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
          .append("', ").append(runTime).append(", toUInt8(").append(k - 1).append("), '").append(stepName).append("',\n")
          .append("       countIf(winning_depth >= ").append(k).append("),\n")
          .append("       countIf(winning_depth >= ").append(k)
          .append(") * 100.0 / greatest(count(), 1),\n");
      if (k == 1) {
        sql.append("       CAST(NULL AS Nullable(Int64))\n");
      } else {
        String lo = Integer.toString(k - 1);
        String hi = Integer.toString(k);
        String diff =
            "toFloat64(dateDiff('second', tupleElement(chain, "
                + lo
                + "), tupleElement(chain, "
                + hi
                + ")))";
        String cond =
            "winning_depth >= "
                + k
                + " AND tupleElement(chain, "
                + lo
                + ") IS NOT NULL AND tupleElement(chain, "
                + hi
                + ") IS NOT NULL AND tupleElement(chain, "
                + hi
                + ") >= tupleElement(chain, "
                + lo
                + ")";
        sql.append("       accurateCastOrNull(round(quantileExactIf(0.5)(\n")
            .append("         ").append(diff).append(",\n")
            .append("         ").append(cond).append("\n")
            .append("       )), 'Int64')\n");
      }
      sql.append("FROM winners\n");
    }

    return sql.toString();
  }

  /**
   * Builds unordered-funnel INSERT SQL for a single funnel.
   *
   * <p>Semantics match Spark unordered funnels: per identity, find the maximum number of distinct
   * steps completed inside any {@code windowSeconds}-wide forward window; step {@code i} count is
   * the number of identities with at least {@code i+1} distinct steps in that best window.
   *
   * <p>Median step duration is always {@code NULL} for unordered funnels.
   */
  public static String buildInsertSqlUnordered(FunnelDefinitionRow def) {
    return buildInsertSqlUnordered(def, runTimeLiteral());
  }

  /** Overload for a caller-supplied {@code RunTime} literal; see {@link #newRunTimeLiteral()}. */
  public static String buildInsertSqlUnordered(FunnelDefinitionRow def, String runTime) {
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
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(def.getFunnelType(), def.getEndTime());

    String eventNameInClause = steps.stream()
        .map(s -> "'" + escape(s.getEventName()) + "'")
        .collect(Collectors.joining(", "));
    String additionalFilters = filters.stream()
        .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
        .collect(Collectors.joining("\n      "));

    String stepRows = buildUnorderedStepRows(steps, funnelId, projectId, runTime);

    StringBuilder sql = new StringBuilder(2048);
    sql.append("INSERT INTO otel.funnel_results\n")
        .append("  (FunnelId, ProjectId, RunTime, StepIndex, StepName, UserCount, ConversionPct, MedianStepSeconds)\n")
        .append("WITH\n")
        .append("  step_events AS (\n")
        .append("    SELECT ").append(groupKey).append(" AS uid,\n")
        .append("           toDateTime(Timestamp) AS FunnelTs,\n")
        .append("           EventName,\n")
        .append("           multiIf(\n");
    for (int i = 0; i < stepCount; i++) {
      sql.append("             EventName = '").append(escape(steps.get(i).getEventName())).append("', ")
          .append(i).append(",\n");
    }
    sql.append("             -1\n")
        .append("           ) AS step_idx\n")
        .append("    FROM otel.otel_logs\n")
        .append("    WHERE ProjectId = '").append(projectId).append("'\n")
        .append("      AND PulseType = 'custom_event'\n")
        .append("      AND Timestamp BETWEEN ").append(startExpr).append(" AND ").append(endExpr).append("\n")
        .append("      AND EventName IN (").append(eventNameInClause).append(")\n");
    if (!additionalFilters.isBlank()) {
      sql.append("      ").append(additionalFilters).append("\n");
    }
    sql.append("  ),\n")
        .append("  window_scores AS (\n")
        .append("    SELECT a.uid,\n")
        .append("           a.FunnelTs AS anchor_ts,\n")
        .append("           uniqExactIf(\n")
        .append("             b.step_idx,\n")
        .append("             b.FunnelTs >= a.FunnelTs\n")
        .append("             AND b.FunnelTs <= a.FunnelTs + INTERVAL ").append(windowSeconds).append(" SECOND\n")
        .append("           ) AS steps_in_window\n")
        .append("    FROM step_events a\n")
        .append("    INNER JOIN step_events b ON a.uid = b.uid\n")
        .append("    GROUP BY a.uid, a.FunnelTs\n")
        .append("  ),\n")
        .append("  best_per_uid AS (\n")
        .append("    SELECT uid, max(steps_in_window) AS max_steps\n")
        .append("    FROM window_scores\n")
        .append("    GROUP BY uid\n")
        .append("  )\n")
        .append(stepRows);

    return sql.toString();
  }

  // ── Drop-off bridge emission ─────────────────────────────────────────────────

  /**
   * Builds the INSERT SQL that populates {@code otel.funnel_session_state} — the per-session
   * drop-off bridge — for one ORDERED funnel. Returns empty for unordered or zero-step
   * funnels (matches Spark's {@code emitBridgeAndRollup} behaviour).
   *
   * <p>Semantics mirror {@link #buildInsertSqlChain(FunnelDefinitionRow, String)} but the
   * identity is ALWAYS {@code SessionId} — the bridge is per-session even for UNIQUE_USERS
   * funnels so OTel signals (crash / trace / replay) have a single concrete moment to anchor
   * to. Dimension carryover (screen, app version, OS, device, trace id) is hydrated by a
   * final left-join back to {@code otel.otel_logs} at the exact event timestamp of the
   * session's furthest-reached step.
   *
   * <p>Every row is stamped with the caller-supplied {@code runTime} so the downstream
   * drop-off DAO can match bridge rows to their {@code funnel_results} run by {@code MAX(RunTime)}.
   */
  public static String buildSessionStateInsertSql(FunnelDefinitionRow def, String runTime) {
    if (isUnorderedFunnel(def)) {
      // Unordered funnels have no well-defined "furthest-step" anchor — skip, as Spark does.
      return "";
    }
    List<FunnelDefinitionStep> steps = deserializeSteps(def.getStepsJson());
    if (steps.isEmpty()) {
      return "";
    }
    List<FunnelAttributeFilter> filters = deserializeFilters(def.getFiltersJson());

    int stepCount = steps.size();
    long windowSeconds = def.getWindowSeconds();
    long funnelId = def.getId();
    String projectId = def.getProjectId();

    String startExpr = ClickhouseAnalyticsQueryUtils.resolveStartExpr(
        def.getFunnelType(), def.getDateRangeDays(), def.getStartTime());
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(def.getFunnelType(), def.getEndTime());

    String eventNameInClause = steps.stream()
        .map(s -> "'" + escape(s.getEventName()) + "'")
        .collect(Collectors.joining(", "));
    String additionalFilters = filters.stream()
        .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
        .collect(Collectors.joining("\n      "));

    StringBuilder sql = new StringBuilder(4096);
    sql.append("INSERT INTO otel.funnel_session_state\n")
        .append("  (FunnelId, ProjectId, RunTime, SessionId, UserId,\n")
        .append("   LastReachedStep, LastReachedStepName, LastReachedAt,\n")
        .append("   DropoffStep, TimeToDropoffSec, ScreenAtDropoff, TraceIdAtDropoff,\n")
        .append("   AppVersion, OsName, OsVersion, Platform, DeviceModel, NetworkProvider, GeoCountry)\n")
        .append("WITH\n");

    // step_events: identity is ALWAYS SessionId for bridge emission, regardless of funnel mode.
    // Skip rows with empty SessionId — they can't be correlated back to OTel signals.
    sql.append("  step_events AS (\n")
        .append("    SELECT SessionId AS uid,\n")
        .append("           toDateTime(Timestamp) AS FunnelTs,\n")
        .append("           EventName\n")
        .append("    FROM otel.otel_logs\n")
        .append("    WHERE ProjectId = '").append(projectId).append("'\n")
        .append("      AND PulseType = 'custom_event'\n")
        .append("      AND SessionId != ''\n")
        .append("      AND Timestamp BETWEEN ").append(startExpr).append(" AND ").append(endExpr).append("\n")
        .append("      AND EventName IN (").append(eventNameInClause).append(")\n");
    if (!additionalFilters.isBlank()) {
      sql.append("      ").append(additionalFilters).append("\n");
    }
    sql.append("  ),\n");

    // attempts / s1..s(N-1): identical chain walk to buildInsertSqlChain.
    sql.append("  attempts AS (\n")
        .append("    SELECT uid, FunnelTs AS t0\n")
        .append("    FROM step_events\n")
        .append("    WHERE EventName = '").append(escape(steps.get(0).getEventName())).append("'\n")
        .append("  )");

    for (int i = 1; i < stepCount; i++) {
      String prevCte = i == 1 ? "attempts" : ("s" + (i - 1));
      String prevAlias = i == 1 ? "a" : ("s" + (i - 1));
      String stepName = escape(steps.get(i).getEventName());

      sql.append(",\n  s").append(i).append(" AS (\n")
          .append("    SELECT ").append(prevAlias).append(".uid, ").append(prevAlias).append(".t0");
      for (int j = 1; j < i; j++) {
        sql.append(", ").append(prevAlias).append(".t").append(j);
      }
      sql.append(",\n           minOrNullIf(e.FunnelTs, e.FunnelTs >= ").append(prevAlias).append(".t")
          .append(i - 1).append(" AND e.FunnelTs <= ").append(prevAlias).append(".t0 + INTERVAL ")
          .append(windowSeconds).append(" SECOND) AS t").append(i).append("\n")
          .append("    FROM ").append(prevCte).append(" AS ").append(prevAlias).append("\n")
          .append("    LEFT JOIN step_events e\n")
          .append("      ON e.uid = ").append(prevAlias).append(".uid\n")
          .append("     AND e.EventName = '").append(stepName).append("'\n")
          .append("    GROUP BY ").append(prevAlias).append(".uid, ").append(prevAlias).append(".t0");
      for (int j = 1; j < i; j++) {
        sql.append(", ").append(prevAlias).append(".t").append(j);
      }
      sql.append("\n  )");
    }

    // scored: depth = 1..N for each attempt.
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

    // winners: pick the deepest attempt per session (earliest t0 breaks ties).
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
        .append("  ),\n");

    // anchors: extract LastReachedAt (= chain[winning_depth]) and t0 (= chain[1]).
    sql.append("  anchors AS (\n")
        .append("    SELECT uid AS SessionId,\n")
        .append("           toUInt8(winning_depth - 1) AS LastReachedStep,\n")
        .append("           tupleElement(chain, 1) AS T0,\n")
        .append("           multiIf(\n");
    for (int k = stepCount; k >= 2; k--) {
      sql.append("             winning_depth = ").append(k)
          .append(", tupleElement(chain, ").append(k).append("),\n");
    }
    sql.append("             tupleElement(chain, 1)\n")
        .append("           ) AS LastReachedAt\n")
        .append("    FROM winners\n")
        .append("  )\n");

    // Hydrate dimensions by joining back to otel_logs on (SessionId, Timestamp).
    // Using any() (not argMax) — all rows in the group share the same timestamp.
    sql.append("SELECT toUInt64(").append(funnelId).append("),\n")
        .append("       '").append(projectId).append("',\n")
        .append("       ").append(runTime).append(",\n")
        .append("       a.SessionId,\n")
        .append("       any(l.UserId) AS UserId,\n")
        .append("       a.LastReachedStep,\n")
        .append("       multiIf(\n");
    for (int i = 0; i < stepCount; i++) {
      sql.append("         a.LastReachedStep = ").append(i)
          .append(", '").append(escape(steps.get(i).getEventName())).append("',\n");
    }
    sql.append("         ''\n")
        .append("       ) AS LastReachedStepName,\n")
        .append("       toDateTime64(a.LastReachedAt, 3, 'UTC') AS LastReachedAt,\n")
        .append("       toInt8(if(a.LastReachedStep >= ").append(stepCount - 1)
        .append(", -1, a.LastReachedStep + 1)) AS DropoffStep,\n")
        .append("       toInt64(toUnixTimestamp(a.LastReachedAt) - toUnixTimestamp(a.T0)) AS TimeToDropoffSec,\n")
        .append("       any(ifNull(l.LogAttributes['screen.name'], '')) AS ScreenAtDropoff,\n")
        .append("       any(l.TraceId) AS TraceIdAtDropoff,\n")
        .append("       any(l.AppVersion) AS AppVersion,\n")
        .append("       any(l.Platform)   AS OsName,\n")
        .append("       any(l.OsVersion)  AS OsVersion,\n")
        .append("       any(l.Platform)   AS Platform,\n")
        .append("       any(l.DeviceModel) AS DeviceModel,\n")
        .append("       any(l.NetworkProvider) AS NetworkProvider,\n")
        .append("       any(l.GeoCountry) AS GeoCountry\n")
        .append("FROM anchors a\n")
        .append("LEFT JOIN otel.otel_logs l\n")
        .append("  ON l.ProjectId = '").append(projectId).append("'\n")
        .append(" AND l.SessionId = a.SessionId\n")
        .append(" AND toDateTime(l.Timestamp) = a.LastReachedAt\n")
        .append("GROUP BY a.SessionId, a.LastReachedStep, a.LastReachedAt, a.T0\n");

    return sql.toString();
  }

  /**
   * Builds the INSERT SQL that populates {@code otel.funnel_user_state} — the per-user rollup —
   * by reading from the just-written {@code funnel_session_state} rows for the same
   * {@code (ProjectId, FunnelId, RunTime)}. Must be executed AFTER
   * {@link #buildSessionStateInsertSql(FunnelDefinitionRow, String)}.
   *
   * <p>Only meaningful for {@code UNIQUE_USERS} funnels; returns empty for SESSIONS funnels
   * (matches Spark).
   *
   * <p>Canonical session per user = furthest step, latest timestamp on ties (implemented via
   * {@code argMax(..., (LastReachedStep, LastReachedAt))}).
   */
  public static String buildUserStateInsertSql(FunnelDefinitionRow def, String runTime) {
    if (isUnorderedFunnel(def)) {
      return "";
    }
    if ("SESSIONS".equalsIgnoreCase(def.getMode())) {
      return "";
    }
    List<FunnelDefinitionStep> steps = deserializeSteps(def.getStepsJson());
    if (steps.isEmpty()) {
      return "";
    }

    long funnelId = def.getId();
    String projectId = def.getProjectId();

    StringBuilder sql = new StringBuilder(1024);
    sql.append("INSERT INTO otel.funnel_user_state\n")
        .append("  (FunnelId, ProjectId, RunTime, UserId,\n")
        .append("   MaxReachedStep, DropoffStep,\n")
        .append("   CanonicalSessionId, CanonicalLastReachedAt, CanonicalTraceIdAtDropoff,\n")
        .append("   CanonicalScreenAtDropoff,\n")
        .append("   AppVersion, OsName, OsVersion, Platform, DeviceModel, NetworkProvider, GeoCountry,\n")
        .append("   SessionAttempts)\n")
        .append("SELECT toUInt64(").append(funnelId).append("),\n")
        .append("       '").append(projectId).append("',\n")
        .append("       ").append(runTime).append(",\n")
        .append("       UserId,\n")
        .append("       toUInt8(max(LastReachedStep)) AS MaxReachedStep,\n")
        .append("       toInt8(if(sum(DropoffStep = -1) > 0, -1, max(LastReachedStep) + 1)) AS DropoffStep,\n")
        .append("       argMax(SessionId, (LastReachedStep, LastReachedAt)) AS CanonicalSessionId,\n")
        .append("       argMax(LastReachedAt, (LastReachedStep, LastReachedAt)) AS CanonicalLastReachedAt,\n")
        .append("       argMax(TraceIdAtDropoff, (LastReachedStep, LastReachedAt)) AS CanonicalTraceIdAtDropoff,\n")
        .append("       argMax(ScreenAtDropoff, (LastReachedStep, LastReachedAt)) AS CanonicalScreenAtDropoff,\n")
        .append("       argMax(AppVersion, (LastReachedStep, LastReachedAt)),\n")
        .append("       argMax(OsName, (LastReachedStep, LastReachedAt)),\n")
        .append("       argMax(OsVersion, (LastReachedStep, LastReachedAt)),\n")
        .append("       argMax(Platform, (LastReachedStep, LastReachedAt)),\n")
        .append("       argMax(DeviceModel, (LastReachedStep, LastReachedAt)),\n")
        .append("       argMax(NetworkProvider, (LastReachedStep, LastReachedAt)),\n")
        .append("       argMax(GeoCountry, (LastReachedStep, LastReachedAt)),\n")
        .append("       toUInt32(count()) AS SessionAttempts\n")
        .append("FROM otel.funnel_session_state\n")
        .append("WHERE ProjectId = '").append(projectId).append("'\n")
        .append("  AND FunnelId = ").append(funnelId).append("\n")
        .append("  AND RunTime = ").append(runTime).append("\n")
        .append("  AND UserId != ''\n")
        .append("GROUP BY UserId\n");

    return sql.toString();
  }

  // ── Legacy windowFunnel builders (kept as backup) ────────────────────────────

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

    String groupKey = ClickhouseAnalyticsQueryUtils.resolveMaterializedGroupKey(def.getMode());
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
            SELECT %s AS uid, toDateTime(Timestamp) AS FunnelTs, EventName
            FROM otel.otel_logs
            WHERE ProjectId = '%s'
              AND PulseType = 'custom_event'
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
            SELECT UserId,
                   SessionId,
                   Timestamp,
                   toDateTime(Timestamp) AS FunnelTs,
                   EventName
            FROM otel.otel_logs
            WHERE ProjectId = '%s'
              AND PulseType = 'custom_event'
              AND Timestamp >= now() - INTERVAL %d DAY
          ),
        """.formatted(projectId, maxDays));

    List<String> cteNames = new ArrayList<>();
    for (int i = 0; i < defs.size(); i++) {
      FunnelDefinitionRow def = defs.get(i);
      List<FunnelDefinitionStep> steps = deserializeSteps(def.getStepsJson());
      String groupKey = ClickhouseAnalyticsQueryUtils.resolveMaterializedGroupKey(def.getMode());
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

  private static String buildUnorderedStepRows(
      List<FunnelDefinitionStep> steps, long funnelId, String projectId, String runTime) {
    StringBuilder rows = new StringBuilder(1024);
    for (int i = 0; i < steps.size(); i++) {
      if (i > 0) {
        rows.append("UNION ALL\n");
      }
      rows.append("SELECT toUInt64(").append(funnelId).append("), '").append(projectId).append("', ")
          .append(runTime).append(", ")
          .append("toUInt8(").append(i).append("), '").append(escape(steps.get(i).getEventName())).append("',\n")
          .append("       countIf(max_steps >= ").append(i + 1).append("),\n")
          .append("       countIf(max_steps >= ").append(i + 1).append(") * 100.0 / greatest(count(), 1),\n")
          .append("       CAST(NULL AS Nullable(Int64))\n")
          .append("FROM best_per_uid\n");
    }
    return rows.toString();
  }

  private static boolean isUnorderedFunnel(FunnelDefinitionRow def) {
    String stepOrderType = def.getStepOrderType();
    return stepOrderType != null && STEP_ORDER_UNORDERED.equalsIgnoreCase(stepOrderType);
  }
}

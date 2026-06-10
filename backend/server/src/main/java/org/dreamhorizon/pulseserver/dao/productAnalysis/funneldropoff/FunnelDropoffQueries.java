package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff;

import java.util.List;

/**
 * SQL builders for funnel drop-off correlation against {@code otel.funnel_session_state}
 * (and, for UNIQUE_USERS funnels, the derived {@code otel.funnel_user_state}) joined
 * against OTel signal tables: {@code stack_trace_events}, {@code otel_traces}, and
 * {@code session_summary}.
 *
 * <p>Design notes:
 * <ul>
 *   <li>All queries are <b>live</b> (no pre-aggregate table) — cohort size per funnel
 *       run is bounded, and the bridge is partitioned by month so reads are cheap.</li>
 *   <li>For {@code UNIQUE_USERS} funnels we anchor on the user's <b>canonical session</b>
 *       (the one that reached the furthest step), so attribution points to one
 *       concrete OTel moment rather than a blur across attempts.</li>
 *   <li>Lift = droppers-affected-rate / converters-affected-rate. We store it as a
 *       Float64 in the result so the UI can rank without a client-side computation.</li>
 *   <li>Converter cohort uses {@code DropoffStep = -1} (sessions / users that reached
 *       the final step).</li>
 * </ul>
 */
public final class FunnelDropoffQueries {

  /** Window around {@code LastReachedAt} used to attribute OTel signals. */
  private static final int WINDOW_BEFORE_SEC = 30;
  private static final int WINDOW_AFTER_SEC = 60;

  /** Max example session IDs emitted per cause for evidence drill-in. */
  private static final int EXAMPLES_PER_CAUSE = 5;

  /** Cause kinds supported by the side-panel, in display-order preference. */
  public static final List<String> CAUSE_KINDS = List.of(
      "crash", "anr", "non_fatal", "http_5xx", "http_4xx", "frozen_frame");

  private FunnelDropoffQueries() {}

  /**
   * Returns the ranked list of causes for one (funnel × step × run) tuple by reading from
   * the precomputed {@code otel.funnel_dropoff_attribution} table. Should be tried first;
   * if it returns an empty list (no precomputed rows for this run — typically because the
   * funnel compute didn't run after the attribution feature shipped), fall back to
   * {@link #buildCausesSql} which does the live OTel join.
   */
  public static String buildCausesSqlFromAttribution(
      String projectId, long funnelId, int stepIndex, String runTime) {
    String pid = esc(projectId);
    String rtExpr = runTimeExpr(pid, funnelId, runTime);
    // stepIndex from the API is the zero-based step the user dropped FROM (last reached).
    // The compute writes StepIndex = DropoffStep = (lastReachedStep + 1), so a user who
    // reached step 0 and stopped is stored as StepIndex=1. Convert dropped-from → failed-to-reach.
    int targetStep = stepIndex + 1;
    return "SELECT "
        + "  CauseKind AS causeKind, "
        + "  CauseKey AS causeKey, "
        + "  CauseLabel AS causeLabel, "
        + "  DropoffCohort AS dropoffCohort, "
        + "  DropoffAffected AS dropoffAffected, "
        + "  ConverterCohort AS converterCohort, "
        + "  ConverterAffected AS converterAffected, "
        + "  Lift AS lift, "
        + "  arrayStringConcat(ExampleSessions, ',') AS exampleSessions "
        + "FROM otel.funnel_dropoff_attribution "
        + "WHERE ProjectId = '" + pid + "' "
        + "  AND FunnelId = " + funnelId + " "
        + "  AND RunTime = " + rtExpr + " "
        + "  AND StepIndex = " + targetStep + " "
        + "ORDER BY lift DESC, dropoffAffected DESC "
        + "LIMIT 50";
  }

  /**
   * Returns the ranked list of causes for one (funnel × step × run) tuple via live join
   * against the OTel signal tables. Used as a fallback when the precomputed attribution
   * table has no rows for this run.
   *
   * @param mode {@code UNIQUE_USERS} → anchor on {@code funnel_user_state.CanonicalSessionId};
   *             anything else (SESSIONS / null) → anchor on {@code funnel_session_state.SessionId}.
   */
  public static String buildCausesSql(
      String projectId, long funnelId, int stepIndex, String runTime, String mode) {
    String pid = esc(projectId);
    String rtExpr = runTimeExpr(pid, funnelId, runTime);
    String anchorCte = buildAnchorCte(pid, funnelId, stepIndex, rtExpr, mode);
    String converterCte = buildConverterCte(pid, funnelId, rtExpr, mode);

    // Three parallel anchored sub-aggregates, unioned together. Keeping them as
    // independent CTEs means ClickHouse can short-circuit the cheaper ones when
    // a project has no data for a given signal type (e.g. no crashes yet).
    return "WITH "
        + "droppers AS (" + anchorCte + "), "
        + "converters AS (" + converterCte + "), "
        + "dropper_count AS (SELECT count() AS c FROM droppers), "
        + "converter_count AS (SELECT count() AS c FROM converters), "

        // --- stack_trace_events: crash / anr / non_fatal --------------------
        + "stack_causes AS ("
        + "  SELECT "
        + "    lower(e.PulseType) AS cause_kind, "
        + "    concat(e.ExceptionType, '@', e.ScreenName) AS cause_key, "
        + "    concat(e.ExceptionType, ' @ ', e.ScreenName) AS cause_label, "
        + "    count(DISTINCT d.SessionId) AS d_affected, "
        + "    countIf(DISTINCT d.SessionId, d.IsConverter = 0) AS d_dropper_affected, "
        + "    countIf(DISTINCT d.SessionId, d.IsConverter = 1) AS d_converter_affected, "
        + "    groupArray(" + EXAMPLES_PER_CAUSE + ")("
        + "        if(d.IsConverter = 0, d.SessionId, NULL)) AS examples "
        + "  FROM ("
        + "    SELECT SessionId, LastReachedAt, 0 AS IsConverter FROM droppers "
        + "    UNION ALL "
        + "    SELECT SessionId, LastReachedAt, 1 AS IsConverter FROM converters"
        + "  ) d "
        + "  INNER JOIN otel.stack_trace_events AS e "
        + "    ON e.ProjectId = '" + pid + "' "
        + "   AND e.SessionId = d.SessionId "
        + "   AND e.Timestamp BETWEEN d.LastReachedAt - INTERVAL " + WINDOW_BEFORE_SEC + " SECOND "
        + "                       AND d.LastReachedAt + INTERVAL " + WINDOW_AFTER_SEC + " SECOND "
        + "  WHERE lower(e.PulseType) IN ('crash', 'anr', 'non_fatal') "
        + "  GROUP BY cause_kind, cause_key, cause_label"
        + "), "

        // --- otel_traces: http_5xx / http_4xx -------------------------------
        + "http_causes AS ("
        + "  SELECT "
        + "    if(http_status >= 500, 'http_5xx', 'http_4xx') AS cause_kind, "
        + "    concat(http_method, ' ', http_host, ' ', toString(http_status)) AS cause_key, "
        + "    concat(http_method, ' ', http_host, ' → ', toString(http_status)) AS cause_label, "
        + "    count(DISTINCT d.SessionId) AS d_affected, "
        + "    countIf(DISTINCT d.SessionId, d.IsConverter = 0) AS d_dropper_affected, "
        + "    countIf(DISTINCT d.SessionId, d.IsConverter = 1) AS d_converter_affected, "
        + "    groupArray(" + EXAMPLES_PER_CAUSE + ")("
        + "        if(d.IsConverter = 0, d.SessionId, NULL)) AS examples "
        + "  FROM ("
        + "    SELECT "
        + "      toUInt16OrZero(ifNull(t.SpanAttributes['http.status_code'], "
        + "                            ifNull(t.SpanAttributes['http.response.status_code'], '0'))) AS http_status, "
        + "      lowerUTF8(ifNull(t.SpanAttributes['http.method'], "
        + "                       ifNull(t.SpanAttributes['http.request.method'], ''))) AS http_method, "
        + "      ifNull(t.SpanAttributes['net.peer.name'], "
        + "             ifNull(t.SpanAttributes['server.address'], '')) AS http_host, "
        + "      t.SessionId AS session_id, "
        + "      t.Timestamp AS ts "
        + "    FROM otel.otel_traces AS t "
        + "    WHERE t.ProjectId = '" + pid + "'"
        + "  ) AS h "
        + "  INNER JOIN ("
        + "    SELECT SessionId, LastReachedAt, 0 AS IsConverter FROM droppers "
        + "    UNION ALL "
        + "    SELECT SessionId, LastReachedAt, 1 AS IsConverter FROM converters"
        + "  ) d "
        + "    ON h.session_id = d.SessionId "
        + "   AND h.ts BETWEEN d.LastReachedAt - INTERVAL " + WINDOW_BEFORE_SEC + " SECOND "
        + "                AND d.LastReachedAt + INTERVAL " + WINDOW_AFTER_SEC + " SECOND "
        + "  WHERE h.http_status >= 400 "
        + "  GROUP BY cause_kind, cause_key, cause_label"
        + "), "

        // --- session_summary: frozen_frame flags ---------------------------
        // We treat "session had frozen frames" as a coarse-grained cause; the
        // finer "frozen frame at this screen" join requires reading trace
        // events and is deferred.
        + "frame_causes AS ("
        + "  SELECT "
        + "    'frozen_frame' AS cause_kind, "
        + "    'frozen_frames_in_session' AS cause_key, "
        + "    'Frozen frames detected in session' AS cause_label, "
        + "    count(DISTINCT d.SessionId) AS d_affected, "
        + "    countIf(DISTINCT d.SessionId, d.IsConverter = 0) AS d_dropper_affected, "
        + "    countIf(DISTINCT d.SessionId, d.IsConverter = 1) AS d_converter_affected, "
        + "    groupArray(" + EXAMPLES_PER_CAUSE + ")("
        + "        if(d.IsConverter = 0, d.SessionId, NULL)) AS examples "
        + "  FROM ("
        + "    SELECT SessionId, 0 AS IsConverter FROM droppers "
        + "    UNION ALL "
        + "    SELECT SessionId, 1 AS IsConverter FROM converters"
        + "  ) d "
        + "  INNER JOIN otel.session_summary AS s "
        + "    ON s.ProjectId = '" + pid + "' "
        + "   AND s.sessionId = d.SessionId "
        + "  WHERE s.frozenFrameCount > 0 "
        + "  GROUP BY cause_kind, cause_key, cause_label"
        + ") "

        // --- union + lift --------------------------------------------------
        + "SELECT "
        + "  causes.cause_kind    AS causeKind, "
        + "  causes.cause_key     AS causeKey, "
        + "  causes.cause_label   AS causeLabel, "
        + "  (SELECT c FROM dropper_count) AS dropoffCohort, "
        + "  causes.d_dropper_affected    AS dropoffAffected, "
        + "  (SELECT c FROM converter_count) AS converterCohort, "
        + "  causes.d_converter_affected  AS converterAffected, "
        + "  if(causes.d_converter_affected = 0 OR (SELECT c FROM converter_count) = 0, "
        + "     if(causes.d_dropper_affected > 0, 999.0, 0.0), "
        + "     round( "
        + "       (causes.d_dropper_affected / nullIf((SELECT c FROM dropper_count), 0)) "
        + "       / (causes.d_converter_affected / nullIf((SELECT c FROM converter_count), 0)), "
        + "       3)) AS lift, "
        + "  arrayStringConcat(arrayFilter(x -> x IS NOT NULL, causes.examples), ',') AS exampleSessions "
        + "FROM (SELECT * FROM stack_causes "
        + "      UNION ALL SELECT * FROM http_causes "
        + "      UNION ALL SELECT * FROM frame_causes) AS causes "
        + "WHERE causes.d_dropper_affected > 0 "
        + "ORDER BY lift DESC, dropoffAffected DESC "
        + "LIMIT 50";
  }

  /**
   * Hydrates the side-panel's "View N examples" drill-in for a single cause, returning
   * one row per session with the context needed to build replay / trace links.
   */
  public static String buildEvidenceSql(
      String projectId, long funnelId, int stepIndex, String runTime, String mode,
      List<String> sessionIds) {
    String pid = esc(projectId);
    String rtExpr = runTimeExpr(pid, funnelId, runTime);
    String inList = sessionIds.stream()
        .map(FunnelDropoffQueries::esc)
        .reduce((a, b) -> a + "','" + b)
        .map(s -> "'" + s + "'")
        .orElse("''");

    String table = "UNIQUE_USERS".equalsIgnoreCase(mode)
        ? "otel.funnel_user_state" : "otel.funnel_session_state";
    String sidCol = "UNIQUE_USERS".equalsIgnoreCase(mode)
        ? "CanonicalSessionId" : "SessionId";
    String tsCol = "UNIQUE_USERS".equalsIgnoreCase(mode)
        ? "CanonicalLastReachedAt" : "LastReachedAt";
    String traceCol = "UNIQUE_USERS".equalsIgnoreCase(mode)
        ? "CanonicalTraceIdAtDropoff" : "TraceIdAtDropoff";
    String screenCol = "UNIQUE_USERS".equalsIgnoreCase(mode)
        ? "CanonicalScreenAtDropoff" : "ScreenAtDropoff";
    // For UNIQUE_USERS, DropoffStep applies to the user rollup — match stepIndex+1
    // or -1 (converter). For SESSIONS, same semantics at the session level.
    return "SELECT "
        + sidCol + " AS sessionId, UserId AS userId, "
        + "toString(" + tsCol + ") AS lastReachedAt, "
        + traceCol + " AS traceId, "
        + screenCol + " AS screen, "
        + "AppVersion AS appVersion, Platform AS platform "
        + "FROM " + table + " "
        + "WHERE ProjectId = '" + pid + "' "
        + "AND FunnelId = " + funnelId + " "
        + "AND RunTime = " + rtExpr + " "
        + "AND " + sidCol + " IN (" + inList + ") "
        + "LIMIT " + sessionIds.size();
  }

  // ----- helpers ---------------------------------------------------------

  /**
   * CTE: the dropped cohort at the given step for the given funnel mode.
   * Rows come back as {@code (SessionId, LastReachedAt)} so signal joins are uniform.
   */
  private static String buildAnchorCte(String pid, long funnelId, int stepIndex,
                                        String rtExpr, String mode) {
    // stepIndex from the API is the step the user dropped FROM; the state tables store
    // DropoffStep = the step they failed to reach (= lastReachedStep + 1).
    int dropoffStep = stepIndex + 1;
    if ("UNIQUE_USERS".equalsIgnoreCase(mode)) {
      return "SELECT CanonicalSessionId AS SessionId, CanonicalLastReachedAt AS LastReachedAt "
          + "FROM otel.funnel_user_state "
          + "WHERE ProjectId = '" + pid + "' AND FunnelId = " + funnelId + " "
          + "  AND RunTime = " + rtExpr + " AND DropoffStep = " + dropoffStep;
    }
    return "SELECT SessionId, LastReachedAt "
        + "FROM otel.funnel_session_state "
        + "WHERE ProjectId = '" + pid + "' AND FunnelId = " + funnelId + " "
        + "  AND RunTime = " + rtExpr + " AND DropoffStep = " + dropoffStep;
  }

  /** Converter CTE — rows that reached the final step, same shape as droppers. */
  private static String buildConverterCte(String pid, long funnelId, String rtExpr, String mode) {
    if ("UNIQUE_USERS".equalsIgnoreCase(mode)) {
      return "SELECT CanonicalSessionId AS SessionId, CanonicalLastReachedAt AS LastReachedAt "
          + "FROM otel.funnel_user_state "
          + "WHERE ProjectId = '" + pid + "' AND FunnelId = " + funnelId + " "
          + "  AND RunTime = " + rtExpr + " AND DropoffStep = -1";
    }
    return "SELECT SessionId, LastReachedAt "
        + "FROM otel.funnel_session_state "
        + "WHERE ProjectId = '" + pid + "' AND FunnelId = " + funnelId + " "
        + "  AND RunTime = " + rtExpr + " AND DropoffStep = -1";
  }

  /**
   * Builds a {@code toDateTime64('…',3,'UTC')} expression or {@code
   * (SELECT max(RunTime) FROM otel.funnel_results …)} fallback when {@code runTime}
   * is null. Keeps the panel consistent with the main funnel chart.
   */
  private static String runTimeExpr(String pid, long funnelId, String runTime) {
    if (runTime == null || runTime.isBlank()) {
      return "(SELECT max(RunTime) FROM otel.funnel_results "
          + "WHERE ProjectId = '" + pid + "' AND FunnelId = " + funnelId + ")";
    }
    return "toDateTime64('" + esc(runTime) + "', 3, 'UTC')";
  }

  /** Safe inline quoting for ClickHouse string literals. */
  public static String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}

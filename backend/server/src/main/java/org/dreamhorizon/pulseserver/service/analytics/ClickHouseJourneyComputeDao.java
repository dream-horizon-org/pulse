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
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;

/**
 * Builds ClickHouse INSERT…WITH SQL for journey computation.
 *
 * <p>Reads from {@code otel.otel_logs} with {@code PulseType = 'custom_event'}
 * (materialized from {@code LogAttributes['pulse.type']}).
 * Custom event names are read from the {@code EventName} column.
 * {@link ClickHouseComputeService} passes {@code direction} from the journey row (Spark parity:
 * only {@code "START"} is forward; {@code "END"} otherwise).
 *
 * <p>Active builder (called by {@link #buildInsertSql}):
 * <ul>
 *   <li>{@link #buildInsertSqlArrayWalk(JourneyRow, String)} — single-scan, single GROUP BY uid,
 *       array-slice walk around the anchor. Replaces the legacy two-scan + window-function +
 *       self-join approach. PREWHERE-aware. ~5–10× faster on small replicas.</li>
 *   <li>{@link #buildInsertSqlLegacy(JourneyRow, String)} — original window-function builder,
 *       retained as a fallback for verification.</li>
 * </ul>
 */
@Slf4j
public final class ClickHouseJourneyComputeDao {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DateTimeFormatter RUN_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
  private static final String DIR_END = "END";

  private ClickHouseJourneyComputeDao() {}

  /**
   * A single {@code RunTime} literal used to stamp every row of one journey INSERT. All
   * UNION ALL branches must share the same value so {@code RunTime = (SELECT max(RunTime) ...)}
   * returns every edge for the latest run. Mirrors the funnel DAO.
   */
  private static String runTimeLiteral() {
    return "toDateTime64('" + RUN_TIME_FMT.format(Instant.now()) + "', 3, 'UTC')";
  }

  /**
   * Builds the INSERT SQL for a single journey definition. Used on the on-save path (both AUTO and
   * ONCE modes). {@code direction} must be {@code "START"} or {@code "END"} (from the saved journey).
   *
   * <p>Delegates to {@link #buildInsertSqlArrayWalk(JourneyRow, String)}. The legacy
   * window-function builder is preserved as {@link #buildInsertSqlLegacy(JourneyRow, String)}
   * for parity verification.
   */
  public static String buildInsertSql(JourneyRow def, String direction) {
    return buildInsertSqlArrayWalk(def, direction);
  }

  /**
   * Optimized journey INSERT SQL using a per-uid sorted event array sliced around the anchor.
   *
   * <p>Algorithm (per uid):
   * <ol>
   *   <li>{@code base}: PREWHERE-pushed filtered scan of {@code otel.otel_logs}, narrowed to
   *       this project, time range, and {@code custom_event}. Single scan.</li>
   *   <li>{@code per_uid}: GROUP BY uid, build an array of {@code (ts, EventName)} sorted by
   *       timestamp.</li>
   *   <li>{@code walked}: locate the anchor index (first occurrence for {@code START},
   *       last occurrence for {@code END}); take a depth-bounded array slice around it. Drop
   *       uids that never hit the anchor.</li>
   *   <li>Emit one ENTRY row {@code (-1, '', 0, anchor)} with {@code count(*)} of anchor-hitting
   *       uids, plus one row per consecutive {@code (slice[i], slice[i+1])} pair grouped to
   *       {@code (PosFrom, EventFrom, PosTo, EventTo)} with {@code uniqExact(uid)}.</li>
   * </ol>
   *
   * <p>Improvements over the legacy builder: one scan (was two), no
   * {@code row_number() OVER (PARTITION BY uid)} over the full per-uid history, no
   * self-join on {@code r2.pos = r1.pos + 1}, {@code uniqExact} instead of
   * {@code count(DISTINCT)}, and {@code PREWHERE} on {@code (ProjectId, Timestamp)} so the read
   * stage is byte-bounded.
   *
   * <p>Output schema is identical to the legacy builder; downstream readers
   * ({@link org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.JourneyResultsDao})
   * are unchanged.
   */
  public static String buildInsertSqlArrayWalk(JourneyRow def, String direction) {
    List<FunnelAttributeFilter> filters = deserializeFilters(def.getFiltersJson());

    String additionalFilters = filters.stream()
        .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
        .collect(Collectors.joining("\n      "));

    String groupKey = ClickhouseAnalyticsQueryUtils.resolveMaterializedGroupKey(def.getMode());
    String startExpr = ClickhouseAnalyticsQueryUtils.resolveStartExpr(
        def.getJourneyType(), def.getDateRangeDays(), def.getStartTime());
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(def.getJourneyType(), def.getEndTime());

    boolean isEnd = DIR_END.equalsIgnoreCase(direction);
    String projectId = def.getProjectId();
    long journeyId = def.getId();
    int depth = def.getDepth();
    String anchorEvent = escape(def.getAnchorEvent());
    String runTime = runTimeLiteral();

    StringBuilder sql = new StringBuilder(2048);
    sql.append("INSERT INTO otel.journey_results\n")
        .append("  (JourneyId, ProjectId, RunTime, Direction, PosFrom, EventFrom, PosTo, EventTo, UserCount)\n")
        .append("WITH\n");

    appendBaseCte(sql, groupKey, projectId, startExpr, endExpr, additionalFilters);
    sql.append(",\n");
    appendPerUidCte(sql);
    sql.append(",\n");
    appendWalkedCte(sql, anchorEvent, depth, isEnd);
    sql.append("\n");

    appendEntryAndEdgeSelects(sql, journeyId, projectId, runTime, direction, anchorEvent, isEnd);
    return sql.toString();
  }

  /**
   * Legacy window-function + self-join builder. Retained for parity verification while the new
   * array-walk builder bakes in. Keep behavior identical to the original implementation.
   */
  public static String buildInsertSqlLegacy(JourneyRow def, String direction) {
    List<FunnelAttributeFilter> filters = deserializeFilters(def.getFiltersJson());

    String globalFilterClauses = filters.stream()
        .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
        .collect(Collectors.joining("\n      "));

    String groupKey = ClickhouseAnalyticsQueryUtils.resolveMaterializedGroupKey(def.getMode());
    String startExpr = ClickhouseAnalyticsQueryUtils.resolveStartExpr(
        def.getJourneyType(), def.getDateRangeDays(), def.getStartTime());
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(def.getJourneyType(), def.getEndTime());

    String dirOrder = DIR_END.equalsIgnoreCase(direction) ? "DESC" : "ASC";
    int dirSign = DIR_END.equalsIgnoreCase(direction) ? -1 : 1;

    String projectId = def.getProjectId();
    long journeyId = def.getId();
    int depth = def.getDepth();
    String anchorEvent = escape(def.getAnchorEvent());

    return """
        INSERT INTO otel.journey_results
          (JourneyId, ProjectId, RunTime, Direction, PosFrom, EventFrom, PosTo, EventTo, UserCount)
        WITH
          sessions AS (
            SELECT DISTINCT %s AS gid
            FROM otel.otel_logs
            WHERE ProjectId = '%s'
              AND PulseType = 'custom_event'
              AND Timestamp BETWEEN %s AND %s
              AND EventName = '%s'
              %s
          ),
          positioned AS (
            SELECT l.%s AS gid, l.EventName, l.Timestamp,
              row_number() OVER (PARTITION BY l.%s ORDER BY l.Timestamp %s) AS rn
            FROM otel.otel_logs l
            INNER JOIN sessions s ON l.%s = s.gid
            WHERE ProjectId = '%s'
              AND PulseType = 'custom_event'
              AND l.Timestamp BETWEEN %s AND %s
          ),
          anchor_pos AS (
            SELECT gid, minIf(rn, EventName = '%s') AS anchor_rn
            FROM positioned GROUP BY gid
          ),
          relative AS (
            SELECT p.gid, p.EventName,
              (p.rn - a.anchor_rn) * %d AS pos
            FROM positioned p JOIN anchor_pos a ON p.gid = a.gid
            WHERE p.rn >= a.anchor_rn AND (p.rn - a.anchor_rn) <= %d
          ),
          edges AS (
            SELECT r1.gid, r1.pos AS pf, r1.EventName AS ef, r2.pos AS pt, r2.EventName AS et
            FROM relative r1 JOIN relative r2 ON r1.gid = r2.gid AND r2.pos = r1.pos + 1
          )
        SELECT %d, '%s', now(), '%s', -1, '', 0, '%s', count(DISTINCT gid) FROM anchor_pos
        UNION ALL
        SELECT %d, '%s', now(), '%s', pf, ef, pt, et, count(DISTINCT gid) FROM edges GROUP BY pf, ef, pt, et
        """.formatted(
        groupKey,
        projectId, startExpr, endExpr, anchorEvent, globalFilterClauses,
        groupKey, groupKey, dirOrder, groupKey,
        projectId, startExpr, endExpr,
        anchorEvent,
        dirSign, depth,
        journeyId, projectId, direction, anchorEvent,
        journeyId, projectId, direction);
  }

  /**
   * Builds a batch INSERT SQL covering journeys that share the same {@code direction}
   * ("START" or "END"). Used by the batch cron path.
   *
   * <p>One shared {@code base} scan is emitted; each journey gets its own
   * {@code per_uid_jX} + {@code walked_jX} pair, then UNION ALL emits the ENTRY row plus
   * the edge rows for that journey. No window functions, no self-joins.
   */
  public static String buildBatchInsertSql(List<JourneyRow> defs, String direction) {
    if (defs == null || defs.isEmpty()) {
      return "";
    }

    boolean isEnd = DIR_END.equalsIgnoreCase(direction);
    String projectId = defs.get(0).getProjectId();
    int maxDays = defs.stream().mapToInt(JourneyRow::getDateRangeDays).max().orElse(30);
    String runTime = runTimeLiteral();

    // All journeys in a batch must share a group key (AppInstallationId vs SessionId) so
    // the shared `base` scan can carry a single `gid` column. ClickHouseComputeService
    // groups by mode upstream, but defend against a mixed batch by falling back to
    // single-builder paths.
    String groupKey = ClickhouseAnalyticsQueryUtils.resolveMaterializedGroupKey(defs.get(0).getMode());
    boolean mixedGroupKeys = defs.stream().anyMatch(d ->
        !groupKey.equals(ClickhouseAnalyticsQueryUtils.resolveMaterializedGroupKey(d.getMode())));
    if (mixedGroupKeys) {
      log.warn("buildBatchInsertSql got mixed mode batch ({} journeys); falling back to single-journey "
          + "INSERTs concatenated. Caller should split by mode for best perf.", defs.size());
      StringBuilder fallback = new StringBuilder(2048);
      for (JourneyRow def : defs) {
        if (fallback.length() > 0) {
          fallback.append(";\n");
        }
        fallback.append(buildInsertSqlArrayWalk(def, direction));
      }
      return fallback.toString();
    }

    StringBuilder sb = new StringBuilder(4096);
    sb.append("INSERT INTO otel.journey_results\n")
        .append("  (JourneyId, ProjectId, RunTime, Direction, PosFrom, EventFrom, PosTo, EventTo, UserCount)\n")
        .append("WITH\n");

    // Shared base scan: covers the widest time range across the batch. Per-journey CTEs
    // re-filter via inner WHERE (Timestamp >= now() - INTERVAL <days> DAY) when their range is tighter.
    sb.append("  base AS (\n")
        .append("    SELECT ").append(groupKey).append(" AS gid,\n")
        .append("           toDateTime64(Timestamp, 9) AS ts,\n")
        .append("           EventName\n")
        .append("    FROM otel.otel_logs\n")
        .append("    PREWHERE ProjectId = '").append(projectId).append("'\n")
        .append("         AND Timestamp >= now() - INTERVAL ").append(maxDays).append(" DAY\n")
        .append("    WHERE PulseType = 'custom_event'\n")
        .append("  )");

    List<String> walkedCteNames = new ArrayList<>(defs.size());
    for (int i = 0; i < defs.size(); i++) {
      JourneyRow def = defs.get(i);
      String anchorEvent = escape(def.getAnchorEvent());
      List<FunnelAttributeFilter> filters = deserializeFilters(def.getFiltersJson());
      String filterClauses = filters.stream()
          .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
          .collect(Collectors.joining(" "));
      String tighterFilter = def.getDateRangeDays() < maxDays
          ? "AND ts >= now() - INTERVAL " + def.getDateRangeDays() + " DAY"
          : "";

      String perUid = "per_uid_j" + i;
      String walked = "walked_j" + i;
      walkedCteNames.add(walked);

      sb.append(",\n  ").append(perUid).append(" AS (\n")
          .append("    SELECT gid,\n")
          .append("           arraySort(x -> tuple(x.1, x.2), groupArray(tuple(ts, EventName))) AS ev\n")
          .append("    FROM base\n");
      if (!tighterFilter.isEmpty() || !filterClauses.isBlank()) {
        sb.append("    WHERE 1=1\n");
        if (!tighterFilter.isEmpty()) {
          sb.append("      ").append(tighterFilter).append("\n");
        }
        if (!filterClauses.isBlank()) {
          sb.append("      ").append(filterClauses).append("\n");
        }
      }
      sb.append("    GROUP BY gid\n  )");

      sb.append(",\n  ").append(walked).append(" AS (\n");
      appendWalkedBody(sb, perUid, anchorEvent, def.getDepth(), isEnd);
      sb.append("  )");
    }
    sb.append("\n");

    // ENTRY + edge UNION ALL block per journey.
    sb.append("SELECT * FROM (\n");
    for (int i = 0; i < defs.size(); i++) {
      JourneyRow def = defs.get(i);
      String anchorEvent = escape(def.getAnchorEvent());
      String walked = walkedCteNames.get(i);

      if (i > 0) {
        sb.append("  UNION ALL\n");
      }
      // ENTRY
      sb.append("  SELECT toUInt64(").append(def.getId()).append(") AS JourneyId,\n")
          .append("         '").append(projectId).append("' AS ProjectId,\n")
          .append("         ").append(runTime).append(" AS RunTime,\n")
          .append("         '").append(direction).append("' AS Direction,\n")
          .append("         toInt32(-1) AS PosFrom, '' AS EventFrom,\n")
          .append("         toInt32(0) AS PosTo, '").append(anchorEvent).append("' AS EventTo,\n")
          .append("         toUInt64(count()) AS UserCount\n")
          .append("  FROM ").append(walked).append("\n");
      // Edges
      sb.append("  UNION ALL\n");
      sb.append("  SELECT toUInt64(").append(def.getId()).append("),\n")
          .append("         '").append(projectId).append("',\n")
          .append("         ").append(runTime).append(",\n")
          .append("         '").append(direction).append("',\n")
          .append("         toInt32(tupleElement(edge, 1)), tupleElement(edge, 2),\n")
          .append("         toInt32(tupleElement(edge, 3)), tupleElement(edge, 4),\n")
          .append("         toUInt64(uniqExact(gid))\n")
          .append("  FROM (\n");
      appendEdgeProjection(sb, walked, isEnd);
      sb.append("  )\n")
          .append("  GROUP BY edge\n");
    }
    sb.append(")");
    return sb.toString();
  }

  // ── shared CTE / SELECT builders ────────────────────────────────────────────────

  private static void appendBaseCte(
      StringBuilder sql,
      String groupKey,
      String projectId,
      String startExpr,
      String endExpr,
      String additionalFilters) {
    sql.append("  base AS (\n")
        .append("    SELECT ").append(groupKey).append(" AS gid,\n")
        .append("           toDateTime64(Timestamp, 9) AS ts,\n")
        .append("           EventName\n")
        .append("    FROM otel.otel_logs\n")
        .append("    PREWHERE ProjectId = '").append(projectId).append("'\n")
        .append("         AND Timestamp BETWEEN ").append(startExpr).append(" AND ").append(endExpr).append("\n")
        .append("    WHERE PulseType = 'custom_event'");
    if (!additionalFilters.isBlank()) {
      sql.append("\n      ").append(additionalFilters);
    }
    sql.append("\n  )");
  }

  private static void appendPerUidCte(StringBuilder sql) {
    sql.append("  per_uid AS (\n")
        .append("    SELECT gid,\n")
        .append("           arraySort(x -> tuple(x.1, x.2), groupArray(tuple(ts, EventName))) AS ev\n")
        .append("    FROM base\n")
        .append("    GROUP BY gid\n  )");
  }

  private static void appendWalkedCte(StringBuilder sql, String anchorEvent, int depth, boolean isEnd) {
    sql.append("  walked AS (\n");
    appendWalkedBody(sql, "per_uid", anchorEvent, depth, isEnd);
    sql.append("  )");
  }

  /**
   * Body of the {@code walked} CTE — written into a chosen source CTE name so the same logic
   * can be reused inside batch builds where each journey has its own {@code per_uid_jX}.
   *
   * <p>For START: anchor index = first occurrence; slice = {@code ev[anchor_idx, depth + 1]}.
   * For END:   anchor index = last  occurrence; slice = {@code ev[max(1, anchor_idx - depth),
   * min(anchor_idx, depth + 1)]}.
   */
  private static void appendWalkedBody(
      StringBuilder sql, String sourceCte, String anchorEvent, int depth, boolean isEnd) {
    sql.append("    SELECT gid, anchor_idx,\n");
    if (isEnd) {
      sql.append("           arraySlice(\n")
          .append("             ev,\n")
          .append("             greatest(1, anchor_idx - ").append(depth).append("),\n")
          .append("             least(anchor_idx, ").append(depth + 1).append(")\n")
          .append("           ) AS slice\n");
    } else {
      sql.append("           arraySlice(ev, anchor_idx, ").append(depth + 1).append(") AS slice\n");
    }
    sql.append("    FROM (\n")
        .append("      SELECT gid, ev,\n");
    if (isEnd) {
      sql.append("             arrayLastIndex(x -> x = '").append(anchorEvent)
          .append("', arrayMap(x -> x.2, ev)) AS anchor_idx\n");
    } else {
      sql.append("             indexOf(arrayMap(x -> x.2, ev), '").append(anchorEvent)
          .append("') AS anchor_idx\n");
    }
    sql.append("      FROM ").append(sourceCte).append("\n")
        .append("    )\n")
        .append("    WHERE anchor_idx > 0\n");
  }

  private static void appendEntryAndEdgeSelects(
      StringBuilder sql,
      long journeyId,
      String projectId,
      String runTime,
      String direction,
      String anchorEvent,
      boolean isEnd) {
    // ENTRY row
    sql.append("SELECT toUInt64(").append(journeyId).append("), '").append(projectId)
        .append("', ").append(runTime).append(", '").append(direction).append("',\n")
        .append("       toInt32(-1), '', toInt32(0), '").append(anchorEvent).append("',\n")
        .append("       toUInt64(count())\n")
        .append("FROM walked\n");
    sql.append("UNION ALL\n");
    // Edge rows
    sql.append("SELECT toUInt64(").append(journeyId).append("), '").append(projectId)
        .append("', ").append(runTime).append(", '").append(direction).append("',\n")
        .append("       toInt32(tupleElement(edge, 1)), tupleElement(edge, 2),\n")
        .append("       toInt32(tupleElement(edge, 3)), tupleElement(edge, 4),\n")
        .append("       toUInt64(uniqExact(gid))\n")
        .append("FROM (\n");
    appendEdgeProjection(sql, "walked", isEnd);
    sql.append(")\n")
        .append("GROUP BY edge\n");
  }

  /**
   * Builds the {@code SELECT gid, arrayJoin(arrayMap(...)) AS edge FROM <walkedCte>} that
   * expands each uid's slice into consecutive (pos, event) pairs. {@code range(1, length(slice))}
   * yields indices {@code 1..length(slice)-1} so {@code slice[i+1]} stays in bounds.
   */
  private static void appendEdgeProjection(StringBuilder sql, String walkedCte, boolean isEnd) {
    sql.append("  SELECT gid,\n")
        .append("         arrayJoin(\n")
        .append("           arrayMap(\n");
    if (isEnd) {
      sql.append("             i -> tuple(\n")
          .append("               i      - length(slice), tupleElement(slice[i],     2),\n")
          .append("               i + 1  - length(slice), tupleElement(slice[i + 1], 2)\n")
          .append("             ),\n");
    } else {
      sql.append("             i -> tuple(\n")
          .append("               i - 1, tupleElement(slice[i],     2),\n")
          .append("               i,     tupleElement(slice[i + 1], 2)\n")
          .append("             ),\n");
    }
    sql.append("             range(1, length(slice))\n")
        .append("           )\n")
        .append("         ) AS edge\n")
        .append("  FROM ").append(walkedCte).append("\n");
  }

  private static List<FunnelAttributeFilter> deserializeFilters(String filtersJson) {
    if (filtersJson == null || filtersJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return MAPPER.readValue(filtersJson, new TypeReference<List<FunnelAttributeFilter>>() {});
    } catch (Exception e) {
      log.error("Failed to deserialize journey filters JSON: {}", filtersJson, e);
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

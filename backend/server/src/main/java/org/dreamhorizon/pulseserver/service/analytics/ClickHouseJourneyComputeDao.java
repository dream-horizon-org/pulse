package org.dreamhorizon.pulseserver.service.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>Reads from {@code otel.otel_logs} with {@code LogAttributes['pulse.type'] = 'custom_event'}.
 * Called twice per journey: once for direction "START" and once for "END".
 */
@Slf4j
public final class ClickHouseJourneyComputeDao {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ClickHouseJourneyComputeDao() {}

  /**
   * Builds the INSERT SQL for a single journey definition. Used on the on-save path (both AUTO and
   * ONCE modes). Must be called twice per journey: once with {@code direction="START"} and once
   * with {@code direction="END"}.
   */
  public static String buildInsertSql(JourneyRow def, String direction) {
    List<FunnelAttributeFilter> filters = deserializeFilters(def.getFiltersJson());

    String globalFilterClauses = filters.stream()
        .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
        .collect(Collectors.joining("\n      "));

    String groupKey = ClickhouseAnalyticsQueryUtils.resolveGroupKey(def.getJourneyType());
    String startExpr = ClickhouseAnalyticsQueryUtils.resolveStartExpr(
        def.getMode(), def.getDateRangeDays(), def.getStartTime());
    String endExpr = ClickhouseAnalyticsQueryUtils.resolveEndExpr(def.getMode(), def.getEndTime());

    String dirOrder = "END".equalsIgnoreCase(direction) ? "DESC" : "ASC";
    int dirSign = "END".equalsIgnoreCase(direction) ? -1 : 1;

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
            WHERE ResourceAttributes['project.id'] = '%s'
              AND LogAttributes['pulse.type'] = 'custom_event'
              AND Timestamp BETWEEN %s AND %s
              AND Body = '%s'
              %s
          ),
          positioned AS (
            SELECT l.%s AS gid, l.Body AS EventName, l.Timestamp,
              row_number() OVER (PARTITION BY l.%s ORDER BY l.Timestamp %s) AS rn
            FROM otel.otel_logs l
            INNER JOIN sessions s ON l.%s = s.gid
            WHERE ResourceAttributes['project.id'] = '%s'
              AND LogAttributes['pulse.type'] = 'custom_event'
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
            WHERE abs(p.rn - a.anchor_rn) <= %d
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
   * Builds a batch INSERT SQL covering all AUTO journeys for a single project in one query.
   * Used by the batch cron path. Must be called twice: once for "START" and once for "END".
   *
   * <p>Each journey gets its own CTE chain referencing the shared {@code raw} CTE.
   */
  public static String buildBatchInsertSql(List<JourneyRow> defs, String direction) {
    if (defs == null || defs.isEmpty()) {
      return "";
    }

    String projectId = defs.get(0).getProjectId();
    int maxDays = defs.stream().mapToInt(JourneyRow::getDateRangeDays).max().orElse(30);
    String dirOrder = "END".equalsIgnoreCase(direction) ? "DESC" : "ASC";
    int dirSign = "END".equalsIgnoreCase(direction) ? -1 : 1;

    StringBuilder sb = new StringBuilder();
    sb.append("""
        INSERT INTO otel.journey_results
          (JourneyId, ProjectId, RunTime, Direction, PosFrom, EventFrom, PosTo, EventTo, UserCount)
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

    List<String> edgesCteNames = new ArrayList<>();
    List<String> anchorPosCteNames = new ArrayList<>();

    for (int i = 0; i < defs.size(); i++) {
      JourneyRow def = defs.get(i);
      String anchorEvent = escape(def.getAnchorEvent());
      String groupAlias = "SESSIONS".equalsIgnoreCase(def.getJourneyType()) ? "SessionId" : "UserId";
      List<FunnelAttributeFilter> filters = deserializeFilters(def.getFiltersJson());
      String filterClauses = filters.stream()
          .map(ClickhouseAnalyticsConstantsMapper::toSqlClause)
          .collect(Collectors.joining(" "));
      String tighterFilter = def.getDateRangeDays() < maxDays
          ? "AND Timestamp >= now() - INTERVAL " + def.getDateRangeDays() + " DAY"
          : "";

      String sessions = "sess_j" + i;
      String positioned = "pos_j" + i;
      String anchorPos = "anc_j" + i;
      String relative = "rel_j" + i;
      String edges = "edg_j" + i;
      edgesCteNames.add(edges);
      anchorPosCteNames.add(anchorPos);

      sb.append("  ").append(sessions).append(" AS (\n");
      sb.append("    SELECT DISTINCT ").append(groupAlias).append(" AS gid\n");
      sb.append("    FROM raw WHERE EventName = '").append(anchorEvent).append("' ").append(tighterFilter)
          .append(" ").append(filterClauses).append("\n  ),\n");

      sb.append("  ").append(positioned).append(" AS (\n");
      sb.append("    SELECT r.").append(groupAlias).append(" AS gid, r.EventName, r.Timestamp,\n");
      sb.append("      row_number() OVER (PARTITION BY r.").append(groupAlias)
          .append(" ORDER BY r.Timestamp ").append(dirOrder).append(") AS rn\n");
      sb.append("    FROM raw r INNER JOIN ").append(sessions)
          .append(" s ON r.").append(groupAlias).append(" = s.gid\n");
      if (!tighterFilter.isEmpty()) {
        sb.append("    ").append(tighterFilter).append("\n");
      }
      sb.append("  ),\n");

      sb.append("  ").append(anchorPos).append(" AS (\n");
      sb.append("    SELECT gid, minIf(rn, EventName = '").append(anchorEvent).append("') AS anchor_rn\n");
      sb.append("    FROM ").append(positioned).append(" GROUP BY gid\n  ),\n");

      sb.append("  ").append(relative).append(" AS (\n");
      sb.append("    SELECT p.gid, p.EventName, (p.rn - a.anchor_rn) * ").append(dirSign)
          .append(" AS pos\n");
      sb.append("    FROM ").append(positioned).append(" p JOIN ").append(anchorPos)
          .append(" a ON p.gid = a.gid WHERE abs(p.rn - a.anchor_rn) <= ").append(def.getDepth())
          .append("\n  ),\n");

      sb.append("  ").append(edges).append(" AS (\n");
      sb.append("    SELECT r1.gid, r1.pos AS pf, r1.EventName AS ef, r2.pos AS pt, r2.EventName AS et\n");
      sb.append("    FROM ").append(relative).append(" r1 JOIN ").append(relative)
          .append(" r2 ON r1.gid = r2.gid AND r2.pos = r1.pos + 1\n  )");
      if (i < defs.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }

    // ENTRY + edge rows per journey via UNION ALL
    for (int i = 0; i < defs.size(); i++) {
      JourneyRow def = defs.get(i);
      String anchorEvent = escape(def.getAnchorEvent());
      if (i == 0) {
        sb.append("SELECT * FROM (\n");
      }
      sb.append("  SELECT ").append(def.getId()).append(", '").append(projectId)
          .append("', now(), '").append(direction).append("', -1, '', 0, '").append(anchorEvent)
          .append("', count(DISTINCT gid) FROM ").append(anchorPosCteNames.get(i)).append("\n");
      sb.append("  UNION ALL\n");
      sb.append("  SELECT ").append(def.getId()).append(", '").append(projectId)
          .append("', now(), '").append(direction)
          .append("', pf, ef, pt, et, count(DISTINCT gid) FROM ").append(edgesCteNames.get(i))
          .append(" GROUP BY pf, ef, pt, et\n");
      if (i < defs.size() - 1) {
        sb.append("  UNION ALL\n");
      }
    }
    sb.append(")");

    return sb.toString();
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

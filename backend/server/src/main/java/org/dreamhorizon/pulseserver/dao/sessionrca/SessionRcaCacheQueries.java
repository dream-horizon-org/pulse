package org.dreamhorizon.pulseserver.dao.sessionrca;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SQL for {@code otel.session_rca_snapshot} (ReplacingMergeTree, keyed by ProjectId + date).
 * Uses clickhouse-r2dbc named parameters for all values — JSON blobs are never string-interpolated.
 */
public final class SessionRcaCacheQueries {

  /** Server-trusted store for precomputed session RCA rows (not a generic cache). */
  public static final String TABLE = "otel.session_rca_snapshot";

  private static final String SELECT_COLUMNS =
      "SELECT ProjectId, date, window_end_utc, mode, baseline, segments, cached_at FROM ";

  private static final DateTimeFormatter DATE_TIME_64 =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

  private SessionRcaCacheQueries() {}

  /**
   * @param projectId project id
   * @param dateIso anchor date {@code yyyy-MM-dd}
   */
  public static BoundStatement buildSelectByKey(String projectId, String dateIso) {
    String sql =
        SELECT_COLUMNS
            + TABLE
            + " WHERE ProjectId = :srca_snap_p0 AND date = toDate(:srca_snap_p1)";
    return new BoundStatement(
        sql,
        List.of("srca_snap_p0", "srca_snap_p1"),
        List.of(projectId == null ? "" : projectId, dateIso == null ? "" : dateIso));
  }

  public static BoundStatement buildInsert(
      String projectId,
      String dateIso,
      Instant windowEndExclusiveUtc,
      String mode,
      String baselineJson,
      String segmentsJson,
      LocalDateTime cachedAt) {
    String windowEndStr = DATE_TIME_64.format(windowEndExclusiveUtc);
    String cachedAtStr =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .format(cachedAt.atOffset(ZoneOffset.UTC));
    String baseline = baselineJson == null || baselineJson.isBlank() ? "{}" : baselineJson;
    String segments = segmentsJson == null || segmentsJson.isBlank() ? "[]" : segmentsJson;

    String sql =
        "INSERT INTO "
            + TABLE
            + " (ProjectId, date, window_end_utc, mode, baseline, segments, cached_at) VALUES ("
            + ":srca_snap_p0, toDate(:srca_snap_p1), toDateTime64(:srca_snap_p2, 3, 'UTC'), "
            + ":srca_snap_p3, :srca_snap_p4, :srca_snap_p5, toDateTime64(:srca_snap_p6, 3, 'UTC'))";
    return new BoundStatement(
        sql,
        List.of(
            "srca_snap_p0",
            "srca_snap_p1",
            "srca_snap_p2",
            "srca_snap_p3",
            "srca_snap_p4",
            "srca_snap_p5",
            "srca_snap_p6"),
        List.of(
            projectId == null ? "" : projectId,
            dateIso == null ? "" : dateIso,
            windowEndStr,
            mode == null ? "" : mode,
            baseline,
            segments,
            cachedAtStr));
  }

  /** SQL plus named bind list for {@link org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService}. */
  public record BoundStatement(String sql, List<String> bindNames, List<Object> bindValues) {}
}

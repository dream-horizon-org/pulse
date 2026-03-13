package org.dreamhorizon.pulseserver.service.rootcause;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.JobCreationMode;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;

/**
 * Builds and runs ClickHouse SELECT against otel_traces for root-cause metrics.
 * Supports baseline (no GROUP BY) and segment queries (GROUP BY dimension(s)).
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RootCauseQueryBuilder {

  private static final String TABLE = "otel_traces";
  private static final DateTimeFormatter CLICKHOUSE_DATETIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Returns total problematic count (distinct spans where Error OR Poor).
   */
  public Single<Long> getTotalProblematicCount(String projectId, String interactionName,
      java.time.Instant startTime, java.time.Instant endTime) {
    String start = CLICKHOUSE_DATETIME.format(startTime);
    String end = CLICKHOUSE_DATETIME.format(endTime);
    String where = "PulseType = 'interaction' AND SpanName = '" + escapeSql(interactionName) + "'"
        + " AND Timestamp >= toDateTime64('" + start + "', 9, 'UTC')"
        + " AND Timestamp <= toDateTime64('" + end + "', 9, 'UTC')"
        + " AND ProjectId = '" + escapeSql(projectId) + "'";
    String query = "SELECT " + ClickhouseConstants.PROBLEMATIC_SPAN_COUNT + " AS cnt FROM " + TABLE + " WHERE " + where;
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .timeoutMs(15000)
        .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
        .projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .map(this::extractSingleLong);
  }

  /**
   * Returns per-segment problematic counts for one dimension.
   */
  public Single<List<ProblematicSegmentCount>> getProblematicCountByDimension(String projectId,
      String interactionName, java.time.Instant startTime, java.time.Instant endTime, String dimensionKey) {
    String start = CLICKHOUSE_DATETIME.format(startTime);
    String end = CLICKHOUSE_DATETIME.format(endTime);
    String where = "PulseType = 'interaction' AND SpanName = '" + escapeSql(interactionName) + "'"
        + " AND Timestamp >= toDateTime64('" + start + "', 9, 'UTC')"
        + " AND Timestamp <= toDateTime64('" + end + "', 9, 'UTC')"
        + " AND ProjectId = '" + escapeSql(projectId) + "'";
    String query = "SELECT " + dimensionKey + ", " + ClickhouseConstants.PROBLEMATIC_SPAN_COUNT + " AS cnt "
        + "FROM " + TABLE + " WHERE " + where
        + " GROUP BY " + dimensionKey + " ORDER BY cnt DESC LIMIT 100";
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .timeoutMs(15000)
        .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
        .projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .map(resp -> parseProblematicSegmentRows(resp, dimensionKey));
  }

  /**
   * Returns per-segment problematic count with dimension filter (for drill-down).
   */
  public Single<List<ProblematicSegmentCount>> getProblematicCountByDimensionWithFilter(String projectId,
      String interactionName, java.time.Instant startTime, java.time.Instant endTime,
      Map<String, String> currentFilters, String nextDimensionKey) {
    String start = CLICKHOUSE_DATETIME.format(startTime);
    String end = CLICKHOUSE_DATETIME.format(endTime);
    List<String> conditions = new ArrayList<>();
    conditions.add("PulseType = 'interaction'");
    conditions.add("SpanName = '" + escapeSql(interactionName) + "'");
    conditions.add("Timestamp >= toDateTime64('" + start + "', 9, 'UTC')");
    conditions.add("Timestamp <= toDateTime64('" + end + "', 9, 'UTC')");
    conditions.add("ProjectId = '" + escapeSql(projectId) + "'");
    if (currentFilters != null) {
      for (Map.Entry<String, String> e : currentFilters.entrySet()) {
        conditions.add(e.getKey() + " = '" + escapeSql(e.getValue()) + "'");
      }
    }
    String where = String.join(" AND ", conditions);
    String query = "SELECT " + nextDimensionKey + ", " + ClickhouseConstants.PROBLEMATIC_SPAN_COUNT + " AS cnt "
        + "FROM " + TABLE + " WHERE " + where
        + " GROUP BY " + nextDimensionKey + " ORDER BY cnt DESC LIMIT 100";
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .timeoutMs(15000)
        .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
        .projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .map(resp -> parseProblematicSegmentRows(resp, nextDimensionKey));
  }

  private long extractSingleLong(GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    List<Map<String, Object>> rows = parseRows(response);
    if (rows.isEmpty()) return 0L;
    Object v = rows.get(0).get("cnt");
    if (v == null) return 0L;
    if (v instanceof Number) return ((Number) v).longValue();
    try {
      return Long.parseLong(v.toString());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private List<ProblematicSegmentCount> parseProblematicSegmentRows(
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response, String dimensionKey) {
    List<Map<String, Object>> rows = parseRows(response);
    List<ProblematicSegmentCount> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String dimValue = Objects.toString(row.get(dimensionKey), "").trim();
      Object cnt = row.get("cnt");
      long count = cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
      result.add(new ProblematicSegmentCount(dimensionKey, dimValue, count));
    }
    return result;
  }

  /** Result row for problematic count by dimension. */
  public static final class ProblematicSegmentCount {
    public final String dimensionKey;
    public final String dimensionValue;
    public final long problematicCount;

    public ProblematicSegmentCount(String dimensionKey, String dimensionValue, long problematicCount) {
      this.dimensionKey = dimensionKey;
      this.dimensionValue = dimensionValue;
      this.problematicCount = problematicCount;
    }
  }

  /**
   * Runs the metrics query and returns rows as list of maps.
   */
  public Single<List<Map<String, Object>>> execute(RootCauseQueryRequest request) {
    String query = buildQuery(request);
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .timeoutMs(30000)
        .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
        .projectId(request.getProjectId())
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .map(this::parseRows);
  }

  private String buildQuery(RootCauseQueryRequest request) {
    String selectClause = buildSelectClause(request.getMetricKeys(), request.getGroupByDimensions());
    String whereClause = buildWhereClause(request);
    String groupByClause = request.getGroupByDimensions() != null && !request.getGroupByDimensions().isEmpty()
        ? " GROUP BY " + String.join(", ", request.getGroupByDimensions())
        : "";
    String orderByClause = request.getGroupByDimensions() != null && !request.getGroupByDimensions().isEmpty()
        ? " ORDER BY volume DESC"
        : "";
    return String.format("SELECT %s FROM %s WHERE %s%s%s LIMIT %d",
        selectClause, TABLE, whereClause, groupByClause, orderByClause, request.getLimit());
  }

  private String buildSelectClause(List<String> metricKeys, List<String> groupByDimensions) {
    List<String> parts = new ArrayList<>();
    if (groupByDimensions != null) {
      for (String dim : groupByDimensions) {
        parts.add(dim + " AS " + dim);
      }
    }
    List<String> keys = metricKeys != null ? metricKeys : RootCauseMetricsRegistry.METRIC_KEYS;
    for (String key : keys) {
      String expr = RootCauseMetricsRegistry.METRIC_EXPRESSIONS.get(key);
      if (expr != null) {
        parts.add(expr + " AS " + key);
      }
    }
    return String.join(", ", parts);
  }

  private String buildWhereClause(RootCauseQueryRequest request) {
    String start = CLICKHOUSE_DATETIME.format(request.getStartTime());
    String end = CLICKHOUSE_DATETIME.format(request.getEndTime());
    List<String> conditions = new ArrayList<>();
    conditions.add("PulseType = 'interaction'");
    conditions.add("SpanName = '" + escapeSql(request.getInteractionName()) + "'");
    conditions.add("Timestamp >= toDateTime64('" + start + "', 9, 'UTC')");
    conditions.add("Timestamp <= toDateTime64('" + end + "', 9, 'UTC')");
    conditions.add("ProjectId = '" + escapeSql(request.getProjectId()) + "'");
    Map<String, String> filters = request.getDimensionFilters();
    if (filters != null && !filters.isEmpty()) {
      for (Map.Entry<String, String> e : filters.entrySet()) {
        conditions.add(e.getKey() + " = '" + escapeSql(e.getValue()) + "'");
      }
    }
    return String.join(" AND ", conditions);
  }

  private static String escapeSql(String s) {
    if (s == null) return "";
    return s.replace("'", "''").replace("\\", "\\\\");
  }

  private List<Map<String, Object>> parseRows(GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    GetRawUserEventsResponseDto data = response.getData();
    if (data == null || data.getRows() == null) {
      return List.of();
    }
    List<String> fieldNames = data.getSchema().getFields().stream()
        .map(GetRawUserEventsResponseDto.Field::getName)
        .toList();
    List<Map<String, Object>> result = new ArrayList<>();
    for (GetRawUserEventsResponseDto.Row row : data.getRows()) {
      Map<String, Object> map = new LinkedHashMap<>();
      List<GetRawUserEventsResponseDto.RowField> values = row.getRowFields();
      for (int i = 0; i < fieldNames.size() && i < values.size(); i++) {
        Object v = values.get(i).getValue();
        if (v != null) {
          map.put(fieldNames.get(i), parseValue(v));
        }
      }
      result.add(map);
    }
    return result;
  }

  private static Object parseValue(Object v) {
    if (v == null) return null;
    if (v instanceof Number) return ((Number) v).doubleValue();
    String s = v.toString();
    if (s.equals("") || s.equalsIgnoreCase("null")) return null;
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      return s;
    }
  }
}
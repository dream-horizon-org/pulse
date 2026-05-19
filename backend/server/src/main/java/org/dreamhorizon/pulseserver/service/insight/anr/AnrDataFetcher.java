package org.dreamhorizon.pulseserver.service.insight.anr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.insight.InsightDataFetcher;

/**
 * Fetches ANR metrics for a given interaction (or all interactions when entityKey is '*') from two
 * tables:
 *
 * <ul>
 *   <li>{@code otel.otel_traces} – interaction spans; carries {@code Events.Name} array with
 *       {@code device.anr} entries when an ANR fires during the interaction.
 *   <li>{@code otel.stack_trace_events} – individual ANR log events; carries full stack trace,
 *       exception type/message, and the {@code Interactions} array of active interaction names.
 * </ul>
 *
 * <p>Three queries run in parallel and are merged into a single {@link JsonNode}:
 *
 * <ol>
 *   <li><b>Aggregate</b> – total_spans, anr_count, anr_rate (span-level), total_sessions,
 *       affected_sessions, affected_users, anr_session_rate.
 *   <li><b>Dimension breakdown</b> – per (Platform × AppVersion × OsVersion) combo ordered by
 *       anr_count DESC; top 15 rows.
 *   <li><b>Top ANR groups</b> – top 10 exception groups from {@code stack_trace_events}, each with
 *       occurrence_count, affected_sessions, representative screens, app-versions, device-models and
 *       OS-versions.
 * </ol>
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AnrDataFetcher implements InsightDataFetcher {

  private static final int LIVE_LOOKBACK_HOURS = 24;
  private static final int BREAKDOWN_LIMIT = 15;
  private static final int TOP_GROUPS_LIMIT = 10;

  private static final String SQL_SELECT = "SELECT ";
  private static final String SQL_FROM_TRACES = "FROM otel.otel_traces ";
  private static final String SQL_WHERE_PROJECT = "WHERE ProjectId = '";
  private static final String SQL_AND_TS_GTE = "AND Timestamp >= '";
  private static final String SQL_AND_TS_LT = "AND Timestamp < '";

  private final ClickhouseQueryService clickhouseQueryService;
  private final ObjectMapper objectMapper;

  // -------------------------------------------------------------------------
  // InsightDataFetcher contract
  // -------------------------------------------------------------------------

  @Override
  public Single<JsonNode> fetchForDate(
      final String projectId, final String entityKey, final LocalDate date) {
    Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return fetchAnrMetrics(projectId, entityKey, start, end, date.toString());
  }

  @Override
  public Single<JsonNode> fetchLive(final String projectId, final String entityKey) {
    Instant end = Instant.now();
    Instant start = end.minusSeconds(LIVE_LOOKBACK_HOURS * 3600L);
    return fetchAnrMetrics(projectId, entityKey, start, end, "live");
  }

  // -------------------------------------------------------------------------
  // Core pipeline
  // -------------------------------------------------------------------------

  private Single<JsonNode> fetchAnrMetrics(
      final String projectId,
      final String entityKey,
      final Instant start,
      final Instant end,
      final String dateLabel) {

    Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> aggregateSingle =
        run(buildAggregateQuery(projectId, entityKey, start, end), projectId);

    Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> breakdownSingle =
        run(buildDimensionBreakdownQuery(projectId, entityKey, start, end), projectId);

    Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> topGroupsSingle =
        run(buildTopAnrGroupsQuery(projectId, entityKey, start, end), projectId);

    return Single.zip(
            aggregateSingle.onErrorReturnItem(emptyResponse()),
            breakdownSingle.onErrorReturnItem(emptyResponse()),
            topGroupsSingle.onErrorReturnItem(emptyResponse()),
            (aggregate, breakdown, topGroups) -> {
              ObjectNode result = objectMapper.createObjectNode();
              result.put("date", dateLabel);
              result.put("projectId", projectId);
              result.put("entityKey", entityKey);
              mergeFirstRow(result, aggregate);
              result.set("dimension_breakdown", buildRowsArray(breakdown));
              result.set("top_anr_groups", buildRowsArray(topGroups));
              return (JsonNode) result;
            })
        .onErrorReturnItem(buildEmptyResult(projectId, entityKey, dateLabel));
  }

  // -------------------------------------------------------------------------
  // Query 1 – aggregate metrics (otel_traces)
  // -------------------------------------------------------------------------

  private static String buildAggregateQuery(
      final String projectId,
      final String entityKey,
      final Instant start,
      final Instant end) {
    String p = escape(projectId);
    String startStr = fmt(start);
    String endStr = fmt(end);

    StringBuilder sql = new StringBuilder();
    sql.append(SQL_SELECT)
        .append("count() AS total_spans, ")
        .append("countIf(has(Events.Name, 'device.anr')) AS anr_count, ")
        .append("if(count() = 0, 0.0,")
        .append("  countIf(has(Events.Name, 'device.anr')) * 100.0 / count()) AS anr_rate, ")
        .append("uniqCombined64(nullIf(SessionId, '')) AS total_sessions, ")
        .append("uniqCombined64If(nullIf(SessionId, ''), has(Events.Name, 'device.anr'))")
        .append("  AS affected_sessions, ")
        .append("uniqCombined64If(nullIf(UserId, ''), has(Events.Name, 'device.anr'))")
        .append("  AS affected_users, ")
        .append("if(uniqCombined64(nullIf(SessionId, '')) = 0, 0.0,")
        .append("  uniqCombined64If(nullIf(SessionId, ''), has(Events.Name, 'device.anr'))")
        .append("  * 100.0 / uniqCombined64(nullIf(SessionId, ''))) AS anr_session_rate ")
        .append(SQL_FROM_TRACES)
        .append(SQL_WHERE_PROJECT).append(p).append("' ")
        .append("AND PulseType = 'interaction' ")
        .append(SQL_AND_TS_GTE).append(startStr).append("' ")
        .append(SQL_AND_TS_LT).append(endStr).append("'");

    appendEntityFilter(sql, entityKey);
    return sql.toString();
  }

  // -------------------------------------------------------------------------
  // Query 2 – dimension breakdown (otel_traces)
  // -------------------------------------------------------------------------

  private static String buildDimensionBreakdownQuery(
      final String projectId,
      final String entityKey,
      final Instant start,
      final Instant end) {
    String p = escape(projectId);
    String startStr = fmt(start);
    String endStr = fmt(end);

    StringBuilder sql = new StringBuilder();
    sql.append(SQL_SELECT)
        .append("Platform, AppVersion, OsVersion, DeviceModel, ")
        .append("count() AS spans, ")
        .append("countIf(has(Events.Name, 'device.anr')) AS anr_count, ")
        .append("if(count() = 0, 0.0,")
        .append("  countIf(has(Events.Name, 'device.anr')) * 100.0 / count()) AS anr_rate ")
        .append(SQL_FROM_TRACES)
        .append(SQL_WHERE_PROJECT).append(p).append("' ")
        .append("AND PulseType = 'interaction' ")
        .append(SQL_AND_TS_GTE).append(startStr).append("' ")
        .append(SQL_AND_TS_LT).append(endStr).append("'");

    appendEntityFilter(sql, entityKey);

    sql.append(" GROUP BY Platform, AppVersion, OsVersion, DeviceModel ")
        .append("ORDER BY anr_count DESC ")
        .append("LIMIT ").append(BREAKDOWN_LIMIT);
    return sql.toString();
  }

  // -------------------------------------------------------------------------
  // Query 3 – top ANR exception groups (stack_trace_events)
  // -------------------------------------------------------------------------

  private static String buildTopAnrGroupsQuery(
      final String projectId,
      final String entityKey,
      final Instant start,
      final Instant end) {
    String p = escape(projectId);
    String startStr = fmt(start);
    String endStr = fmt(end);

    StringBuilder sql = new StringBuilder();
    sql.append(SQL_SELECT)
        .append("GroupId, Signature, ExceptionType, ExceptionMessage, ")
        .append("count() AS occurrence_count, ")
        .append("uniqCombined64(nullIf(SessionId, '')) AS affected_sessions, ")
        .append("groupUniqArray(5)(nullIf(ScreenName, '')) AS top_screens, ")
        .append("groupUniqArray(3)(nullIf(AppVersion, '')) AS top_app_versions, ")
        .append("groupUniqArray(3)(nullIf(DeviceModel, '')) AS top_device_models, ")
        .append("groupUniqArray(3)(nullIf(OsVersion, '')) AS top_os_versions ")
        .append("FROM otel.stack_trace_events ")
        .append(SQL_WHERE_PROJECT).append(p).append("' ")
        .append("AND PulseType = 'device.anr' ")
        .append(SQL_AND_TS_GTE).append(startStr).append("' ")
        .append(SQL_AND_TS_LT).append(endStr).append("'");

    // For non-wildcard entity: filter to ANR events that fired during that interaction.
    // Interactions is Array(LowCardinality(String)) of active interaction names at the time.
    boolean isWildcard = "*".equals(entityKey);
    if (!isWildcard && entityKey != null && !entityKey.isBlank()) {
      sql.append(" AND has(Interactions, '").append(escape(entityKey)).append("')");
    }

    sql.append(" GROUP BY GroupId, Signature, ExceptionType, ExceptionMessage ")
        .append("ORDER BY occurrence_count DESC ")
        .append("LIMIT ").append(TOP_GROUPS_LIMIT);
    return sql.toString();
  }

  // -------------------------------------------------------------------------
  // Response helpers
  // -------------------------------------------------------------------------

  /**
   * Merges every column of the first result row into {@code target} as named fields.
   * No-ops gracefully when the response has no rows.
   */
  private void mergeFirstRow(
      final ObjectNode target,
      final GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    if (response == null || response.getData() == null) {
      return;
    }
    GetRawUserEventsResponseDto data = response.getData();
    List<GetRawUserEventsResponseDto.Field> schema =
        data.getSchema() != null ? data.getSchema().getFields() : null;
    if (schema == null || data.getRows() == null || data.getRows().isEmpty()) {
      return;
    }
    GetRawUserEventsResponseDto.Row row = data.getRows().get(0);
    if (row.getRowFields() == null) {
      return;
    }
    for (int i = 0; i < schema.size(); i++) {
      String name = schema.get(i).getName();
      Object value = i < row.getRowFields().size() ? row.getRowFields().get(i).getValue() : null;
      target.putPOJO(name, value);
    }
  }

  /**
   * Converts all result rows into a JSON array, one object per row with schema-keyed fields.
   * Returns an empty array when the response has no rows or schema.
   */
  private ArrayNode buildRowsArray(
      final GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    ArrayNode array = objectMapper.createArrayNode();
    if (response == null || response.getData() == null) {
      return array;
    }
    GetRawUserEventsResponseDto data = response.getData();
    List<GetRawUserEventsResponseDto.Field> schema =
        data.getSchema() != null ? data.getSchema().getFields() : null;
    if (schema == null || data.getRows() == null) {
      return array;
    }
    for (GetRawUserEventsResponseDto.Row row : data.getRows()) {
      if (row.getRowFields() == null) {
        continue;
      }
      ObjectNode node = objectMapper.createObjectNode();
      for (int i = 0; i < schema.size(); i++) {
        String name = schema.get(i).getName();
        Object value = i < row.getRowFields().size() ? row.getRowFields().get(i).getValue() : null;
        node.putPOJO(name, value);
      }
      array.add(node);
    }
    return array;
  }

  private JsonNode buildEmptyResult(
      final String projectId, final String entityKey, final String dateLabel) {
    ObjectNode result = objectMapper.createObjectNode();
    result.put("date", dateLabel);
    result.put("projectId", projectId);
    result.put("entityKey", entityKey);
    result.put("total_spans", 0);
    result.put("anr_count", 0);
    result.put("anr_rate", 0.0);
    result.put("total_sessions", 0);
    result.put("affected_sessions", 0);
    result.put("affected_users", 0);
    result.put("anr_session_rate", 0.0);
    result.set("dimension_breakdown", objectMapper.createArrayNode());
    result.set("top_anr_groups", objectMapper.createArrayNode());
    return result;
  }

  // -------------------------------------------------------------------------
  // Execution helpers
  // -------------------------------------------------------------------------

  private Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> run(
      final String sql, final String projectId) {
    QueryConfiguration config = QueryConfiguration.newQuery(sql)
        .projectId(projectId)
        .useQueryConditionCache(false)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config);
  }

  /** A typed empty response used as a safe fallback when an individual query fails. */
  private static GetQueryDataResponseDto<GetRawUserEventsResponseDto> emptyResponse() {
    return GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
        .data(GetRawUserEventsResponseDto.builder()
            .schema(new GetRawUserEventsResponseDto.Schema(List.of()))
            .rows(List.of())
            .totalRows(0L)
            .build())
        .jobComplete(true)
        .build();
  }

  // -------------------------------------------------------------------------
  // SQL utilities
  // -------------------------------------------------------------------------

  /** Appends {@code AND SpanName = '<entityKey>'} when entityKey is not wildcard. */
  private static void appendEntityFilter(final StringBuilder sql, final String entityKey) {
    boolean isWildcard = "*".equals(entityKey);
    if (!isWildcard && entityKey != null && !entityKey.isBlank()) {
      sql.append(" AND SpanName = '").append(escape(entityKey)).append("'");
    }
  }

  private static String escape(final String raw) {
    return raw == null ? "" : raw.replace("'", "''");
  }

  private static String fmt(final Instant instant) {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneOffset.UTC)
        .format(instant);
  }
}

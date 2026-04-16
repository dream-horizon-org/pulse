package org.dreamhorizon.pulseserver.service.interaction;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.JobCreationMode;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthRow;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsRow;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionOrderBy;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionRow;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionStatsRow;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionTimeseriesRow;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.TimeRange;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseMetricService implements PerformanceMetricService {

  private final ClickhouseQueryService clickhouseQueryService;
  private final DateTimeFormatter output = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public Single<PerformanceMetricDistributionRes> getMetricDistribution(QueryRequest request) {
    // Select Clause
    String selectClause = "*";
    List<QueryRequest.SelectItem> selects = request.getSelect();
    if (!CollectionUtils.isEmpty(selects)) {
      List<String> clauses = new ArrayList<>();
      for (QueryRequest.SelectItem selectItem : selects) {
        Functions function = selectItem.getFunction();
        String clause = switch (function) {
          case APDEX -> String.format("%s as %s", Functions.APDEX.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.APDEX.getDisplayName()));
          case CRASH -> String.format("%s as %s", Functions.CRASH.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.CRASH.getDisplayName()));
          case ANR -> String.format("%s as %s", Functions.ANR.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ANR.getDisplayName()));
          case FROZEN_FRAME -> String.format("%s as %s", Functions.FROZEN_FRAME.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.FROZEN_FRAME.getDisplayName()));
          case ANALYSED_FRAME -> String.format("%s as %s", Functions.ANALYSED_FRAME.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ANALYSED_FRAME.getDisplayName()));
          case UNANALYSED_FRAME -> String.format("%s as %s", Functions.UNANALYSED_FRAME.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.UNANALYSED_FRAME.getDisplayName()));
          case DURATION_P99 -> String.format("%s as %s", Functions.DURATION_P99.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.DURATION_P99.getDisplayName()));
          case DURATION_P50 -> String.format("%s as %s", Functions.DURATION_P50.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.DURATION_P50.getDisplayName()));
          case DURATION_P95 -> String.format("%s as %s", Functions.DURATION_P95.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.DURATION_P95.getDisplayName()));
          case COL -> String.format("%s as %s", selectItem.getParam().get("field"),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.COL.getDisplayName()));
          case CUSTOM -> String.format("%s as %s", selectItem.getParam().get("expression"),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.CUSTOM.getDisplayName()));
          case INTERACTION_SUCCESS_COUNT -> String.format("%s as %s", Functions.INTERACTION_SUCCESS_COUNT.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.INTERACTION_SUCCESS_COUNT.getDisplayName()));
          case INTERACTION_ERROR_COUNT -> String.format("%s as %s", Functions.INTERACTION_ERROR_COUNT.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.INTERACTION_ERROR_COUNT.getDisplayName()));
          case INTERACTION_ERROR_DISTINCT_USERS -> String.format("%s as %s", Functions.INTERACTION_ERROR_DISTINCT_USERS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.INTERACTION_ERROR_DISTINCT_USERS.getDisplayName()));
          case USER_CATEGORY_AVERAGE -> String.format("%s as %s", Functions.USER_CATEGORY_AVERAGE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.USER_CATEGORY_AVERAGE.getDisplayName()));
          case USER_CATEGORY_GOOD -> String.format("%s as %s", Functions.USER_CATEGORY_GOOD.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.USER_CATEGORY_GOOD.getDisplayName()));
          case USER_CATEGORY_POOR -> String.format("%s as %s", Functions.USER_CATEGORY_POOR.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.USER_CATEGORY_POOR.getDisplayName()));
          case USER_CATEGORY_EXCELLENT -> String.format("%s as %s", Functions.USER_CATEGORY_EXCELLENT.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.USER_CATEGORY_EXCELLENT.getDisplayName()));
          case NET_0 -> String.format("%s as %s", Functions.NET_0.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_0.getDisplayName()));
          case NET_2XX -> String.format("%s as %s", Functions.NET_2XX.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_2XX.getDisplayName()));
          case NET_3XX -> String.format("%s as %s", Functions.NET_3XX.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_3XX.getDisplayName()));
          case NET_4XX -> String.format("%s as %s", Functions.NET_4XX.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_4XX.getDisplayName()));
          case NET_5XX -> String.format("%s as %s", Functions.NET_5XX.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_5XX.getDisplayName()));
          case NET_COUNT -> String.format("%s as %s", Functions.NET_COUNT.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_COUNT.getDisplayName()));
          case CRASH_RATE -> String.format("%s as %s", Functions.CRASH_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.CRASH_RATE.getDisplayName()));
          case ANR_RATE -> String.format("%s as %s", Functions.ANR_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ANR_RATE.getDisplayName()));
          case FROZEN_FRAME_RATE -> String.format("%s as %s", Functions.FROZEN_FRAME_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.FROZEN_FRAME_RATE.getDisplayName()));
          case ERROR_RATE -> String.format("%s as %s", Functions.ERROR_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ERROR_RATE.getDisplayName()));
          case POOR_USER_RATE -> String.format("%s as %s", Functions.POOR_USER_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.POOR_USER_RATE.getDisplayName()));
          case AVERAGE_USER_RATE -> String.format("%s as %s", Functions.AVERAGE_USER_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.AVERAGE_USER_RATE.getDisplayName()));
          case GOOD_USER_RATE -> String.format("%s as %s", Functions.GOOD_USER_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.GOOD_USER_RATE.getDisplayName()));
          case EXCELLENT_USER_RATE -> String.format("%s as %s", Functions.EXCELLENT_USER_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.EXCELLENT_USER_RATE.getDisplayName()));
          case LOAD_TIME -> String.format("%s as %s", Functions.LOAD_TIME.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.LOAD_TIME.getDisplayName()));
          case SCREEN_TIME -> String.format("%s as %s", Functions.SCREEN_TIME.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.SCREEN_TIME.getDisplayName()));
          case SCREEN_DAILY_USERS -> String.format("%s as %s", Functions.SCREEN_DAILY_USERS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.SCREEN_DAILY_USERS.getDisplayName()));
          case NET_4XX_RATE -> String.format("%s as %s", Functions.NET_4XX_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_4XX_RATE.getDisplayName()));
          case NET_5XX_RATE -> String.format("%s as %s", Functions.NET_5XX_RATE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_5XX_RATE.getDisplayName()));
          // Network metrics for alerts (uses PulseType)
          case NET_0_BY_PULSE_TYPE -> String.format("%s as %s", Functions.NET_0_BY_PULSE_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_0_BY_PULSE_TYPE.getDisplayName()));
          case NET_2XX_BY_PULSE_TYPE -> String.format("%s as %s", Functions.NET_2XX_BY_PULSE_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_2XX_BY_PULSE_TYPE.getDisplayName()));
          case NET_3XX_BY_PULSE_TYPE -> String.format("%s as %s", Functions.NET_3XX_BY_PULSE_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_3XX_BY_PULSE_TYPE.getDisplayName()));
          case NET_4XX_BY_PULSE_TYPE -> String.format("%s as %s", Functions.NET_4XX_BY_PULSE_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_4XX_BY_PULSE_TYPE.getDisplayName()));
          case NET_5XX_BY_PULSE_TYPE -> String.format("%s as %s", Functions.NET_5XX_BY_PULSE_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_5XX_BY_PULSE_TYPE.getDisplayName()));
          case NET_COUNT_BY_PULSE_TYPE -> String.format("%s as %s", Functions.NET_COUNT_BY_PULSE_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_COUNT_BY_PULSE_TYPE.getDisplayName()));
          case CRASH_FREE_USERS_PERCENTAGE -> String.format("%s as %s", Functions.CRASH_FREE_USERS_PERCENTAGE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.CRASH_FREE_USERS_PERCENTAGE.getDisplayName()));
          case CRASH_FREE_SESSIONS_PERCENTAGE -> String.format("%s as %s", Functions.CRASH_FREE_SESSIONS_PERCENTAGE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.CRASH_FREE_SESSIONS_PERCENTAGE.getDisplayName()));
          case CRASH_USERS -> String.format("%s as %s", Functions.CRASH_USERS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.CRASH_USERS.getDisplayName()));
          case CRASH_SESSIONS -> String.format("%s as %s", Functions.CRASH_SESSIONS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.CRASH_SESSIONS.getDisplayName()));
          case ALL_USERS -> String.format("%s as %s", Functions.ALL_USERS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ALL_USERS.getDisplayName()));
          case ALL_SESSIONS -> String.format("%s as %s", Functions.ALL_SESSIONS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ALL_SESSIONS.getDisplayName()));
          case ANR_FREE_USERS_PERCENTAGE -> String.format("%s as %s", Functions.ANR_FREE_USERS_PERCENTAGE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ANR_FREE_USERS_PERCENTAGE.getDisplayName()));
          case ANR_FREE_SESSIONS_PERCENTAGE -> String.format("%s as %s", Functions.ANR_FREE_SESSIONS_PERCENTAGE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ANR_FREE_SESSIONS_PERCENTAGE.getDisplayName()));
          case ANR_USERS -> String.format("%s as %s", Functions.ANR_USERS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ANR_USERS.getDisplayName()));
          case ANR_SESSIONS -> String.format("%s as %s", Functions.ANR_SESSIONS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.ANR_SESSIONS.getDisplayName()));
          case NON_FATAL_FREE_USERS_PERCENTAGE -> String.format("%s as %s", Functions.NON_FATAL_FREE_USERS_PERCENTAGE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NON_FATAL_FREE_USERS_PERCENTAGE.getDisplayName()));
          case NON_FATAL_FREE_SESSIONS_PERCENTAGE ->
              String.format("%s as %s", Functions.NON_FATAL_FREE_SESSIONS_PERCENTAGE.getChSelectClause(),
                  Objects.requireNonNullElse(selectItem.getAlias(),
                      Functions.NON_FATAL_FREE_SESSIONS_PERCENTAGE.getDisplayName()));
          case NON_FATAL_USERS -> String.format("%s as %s", Functions.NON_FATAL_USERS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NON_FATAL_USERS.getDisplayName()));
          case NON_FATAL_SESSIONS -> String.format("%s as %s", Functions.NON_FATAL_SESSIONS.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NON_FATAL_SESSIONS.getDisplayName()));
          case TIME_BUCKET -> String.format("%s as %s",
              String.format(Functions.TIME_BUCKET.getChSelectClause(),
                  selectItem.getParam().get("field"),
                  DateTimeUtils.toSeconds(selectItem.getParam().get("bucket")),
                  DateTimeUtils.toSeconds(selectItem.getParam().get("bucket"))),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.TIME_BUCKET.getDisplayName()));
          case ARR_TO_STR ->
              String.format("%s as %s", String.format(Functions.ARR_TO_STR.getChSelectClause(), selectItem.getParam().get("field")),
                  Objects.requireNonNullElse(selectItem.getAlias(), Functions.ARR_TO_STR.getDisplayName()));
        };
        clauses.add(clause);
      }
      selectClause = String.join(",", clauses);
    }

    // Where Clause toDateTime64('${start_time}', 9, 'UTC')
    String timeFilter = String.format("Timestamp >= toDateTime64('%s',9,'UTC')"
            + " AND Timestamp <= toDateTime64('%s',9,'UTC')",
        ZonedDateTime.parse(request.getTimeRange().getStart()).format(output),
        ZonedDateTime.parse(request.getTimeRange().getEnd()).format(output));

    StringBuilder where = new StringBuilder(timeFilter);
    if (!CollectionUtils.isEmpty(request.getFilters())) {
      for (QueryRequest.Filter filter : request.getFilters()) {
        where.append(switch (filter.getOperator()) {
          case LIKE -> String.format(" And %s %s %s", filter.getField(), filter.getOperator().getDisplayName(),
              format(filter.getValue()));
          case IN -> String.format(" And %s %s (%s)", filter.getField(), filter.getOperator().getDisplayName(),
              format(filter.getValue()));
          case EQ -> String.format(" And %s %s %s", filter.getField(), filter.getOperator().getDisplayName(),
              format(List.of(filter.getValue().get(0))));
          case ADDITIONAL -> String.format(" And (%s)", filter.getValue().get(0));
        });
      }
    }

    //Group by
    String groupByClause = "";
    if (!CollectionUtils.isEmpty(request.getGroupBy())) {
      groupByClause = formatGroupBy(request.getGroupBy());
    }

    //Order by
    String orderByClause = "";
    if (!CollectionUtils.isEmpty(request.getOrderBy())) {
      orderByClause = request.getOrderBy()
          .stream()
          .map(o -> o.getField() + " " + o.getDirection())
          .collect(Collectors.joining(", "));
    }

    // Build the query
    String query = "Select %s from %s where %s";
    if (!StringUtils.isEmpty(groupByClause)) {
      query += String.format(" group by %s", groupByClause);
    }
    if (!StringUtils.isEmpty(orderByClause)) {
      query += String.format(" order by %s", orderByClause);
    }
    query += String.format(" limit %d", Objects.requireNonNullElse(request.getLimit(), 100));

    String whereClause = where.toString();

    // From
    String from = switch (request.getDataType()) {
      case TRACES -> "otel_traces";
      case LOGS -> "otel_logs";
      case METRICS -> "otel_metrics";
      case EXCEPTIONS -> "stack_trace_events";
    };

    String finalQuery = String.format(query, selectClause, from, whereClause);
    return clickhouseQueryService.executeQueryOrCreateJob(QueryConfiguration.newQuery(finalQuery)
            .timeoutMs(2000)
            .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
            .projectId(request.getProjectId())
            .build())
        .map(rawRes -> {
          GetRawUserEventsResponseDto.Schema schema = rawRes.data.getSchema();
          List<String> fields = schema.getFields().stream()
              .map(GetRawUserEventsResponseDto.Field::getName)
              .toList();
          List<List<String>> rows = rawRes.data.getRows().stream()
              .map(row -> row.getRowFields().stream()
                  .map(field -> Objects.isNull(field.getValue()) ? "" : field.getValue().toString())
                  .toList())
              .toList();
          return PerformanceMetricDistributionRes.builder()
              .rows(rows)
              .fields(fields)
              .build();
        });
  }

  private String format(List<Object> filters) {
    String substitute = "";
    List<String> formattedfilters = filters.stream()
        .map(id -> {
          boolean check = id instanceof String;
          if (check) {
            return String.format("'%s'", id);
          }
          return String.format("%s", id);
        })
        .collect(Collectors.toList());

    substitute = StringUtils.join(formattedfilters, ',');
    return substitute;
  }

  private String formatGroupBy(List<String> groupBy) {
    String substitute = "";
    List<String> formattedfilters = groupBy.stream()
        .map(id -> String.format("%s", id))
        .collect(Collectors.toList());

    substitute = StringUtils.join(formattedfilters, ',');
    return substitute;
  }

  // ---------------------------------------------------------------------------
  // Static configuration maps for interaction analytics
  // ---------------------------------------------------------------------------

  /** Maps user-friendly event type names to ClickHouse Events.Name values. */
  private static final java.util.Map<String, String> EVENT_TYPE_MAP = java.util.Map.of(
      "crash", ClickhouseConstants.EVENT_CRASH,
      "anr", ClickhouseConstants.EVENT_ANR,
      "error", ClickhouseConstants.EVENT_ERROR,
      "non_fatal", ClickhouseConstants.EVENT_NON_FATAL,
      "frozen_frame", ClickhouseConstants.EVENT_FROZEN_FRAME,
      "network_error", ClickhouseConstants.EVENT_NETWORK_ERROR
  );

  /** Maps LLM-friendly filter keys to ClickHouse column names. */
  private static final java.util.Map<String, String> FILTER_COLUMN_MAP = java.util.Map.of(
      "platform", ClickhouseConstants.COL_PLATFORM,
      "app_version", ClickhouseConstants.COL_APP_VERSION,
      "device", ClickhouseConstants.COL_DEVICE_MODEL,
      "os_version", ClickhouseConstants.COL_OS_VERSION,
      "network", ClickhouseConstants.COL_NETWORK_PROVIDER,
      "region", ClickhouseConstants.COL_GEO_STATE
  );

  /** Dimension configuration for breakdown queries. */
  private record DimensionConfig(String columnName, String alias, List<String> selectItems) {}

  // ---------------------------------------------------------------------------
  // Interaction Analytics API implementations
  // ---------------------------------------------------------------------------

  @Override
  public Single<InteractionHealthRes> getInteractionHealth(
      InteractionHealthReq req) {

    // SELECT — SpanName, COUNT, apdex, success/error counts, user categories, p50
    List<String> selectItems = new ArrayList<>();
    selectItems.add(selectColumn(ClickhouseConstants.COL_SPAN_NAME, "interaction_name"));
    selectItems.add("COUNT() as " + ClickhouseConstants.ALIAS_SPAN_FREQ);
    selectItems.add(selectAs(Functions.APDEX, "apdex"));
    selectItems.add(selectAs(Functions.INTERACTION_SUCCESS_COUNT, "success_count"));
    selectItems.add(selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count"));
    selectItems.add(selectAs(Functions.USER_CATEGORY_EXCELLENT, "user_excellent"));
    selectItems.add(selectAs(Functions.USER_CATEGORY_GOOD, "user_good"));
    selectItems.add(selectAs(Functions.USER_CATEGORY_AVERAGE, "user_avg"));
    selectItems.add(selectAs(Functions.USER_CATEGORY_POOR, "user_poor"));
    selectItems.add(selectAs(Functions.DURATION_P50, "p50"));

    String selectClause = String.join(",", selectItems);

    // WHERE
    StringBuilder whereClause = buildInteractionWhereClause(req.getTimeRange());
    appendInteractionNamesFilter(whereClause, req.getInteractionNames());
    appendUserFilters(whereClause, req.getFilters());

    // GROUP BY, ORDER BY, LIMIT
    int limit = Objects.requireNonNullElse(req.getTopN(), ClickhouseConstants.DEFAULT_INTERACTION_LIMIT);
    String orderByClause = buildOrderByClause(req.getOrderBy(), ClickhouseConstants.ALIAS_SPAN_FREQ + " DESC");

    String query = String.format("Select %s from %s where %s group by %s order by %s limit %d",
        selectClause, ClickhouseConstants.OTEL_TRACES_TABLE, whereClause,
        "interaction_name", orderByClause, limit);

    return clickhouseQueryService.executeQueryOrCreateJob(QueryConfiguration.newQuery(query)
            .timeoutMs(ClickhouseConstants.DEFAULT_QUERY_TIMEOUT_MS)
            .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
            .projectId(req.getProjectId())
            .build())
        .map(rawRes -> {
          List<String> fields = extractFieldNames(rawRes);
          List<InteractionHealthRow> rows
              = rawRes.data.getRows().stream().map(row -> {
            List<Object> values = extractRowValues(row);
            return InteractionHealthRow.builder()
                .name(parseString(values, fields, "interaction_name"))
                .totalCount(parseLong(values, fields, ClickhouseConstants.ALIAS_SPAN_FREQ))
                .apdex(parseDouble(values, fields, "apdex"))
                .successCount(parseLong(values, fields, "success_count"))
                .errorCount(parseLong(values, fields, "error_count"))
                .errorRatePct(computeRate(parseLong(values, fields, "error_count"), parseLong(values, fields, ClickhouseConstants.ALIAS_SPAN_FREQ)))
                .p50Ms(parseDouble(values, fields, "p50"))
                .userExcellent(parseLong(values, fields, "user_excellent"))
                .userGood(parseLong(values, fields, "user_good"))
                .userAverage(parseLong(values, fields, "user_avg"))
                .userPoor(parseLong(values, fields, "user_poor"))
                .build();
          }).toList();
          return InteractionHealthRes.builder()
              .interactions(rows).build();
        });
  }

  @Override
  public Single<InteractionMetricsRes> getInteractionMetrics(
      InteractionMetricsReq req) {
    return Single.defer(() -> {
      if (req.getMetricType() == null) {
        throw new IllegalArgumentException("metricType is required");
      }
      String metricType = req.getMetricType().toUpperCase();

      // METRIC_SELECT_MAP — each metric type maps to a fixed set of SELECT columns
      List<String> selectItems = switch (metricType) {
        case "APDEX" -> List.of(
            selectAs(Functions.APDEX, "apdex"));
        case "LATENCY" -> List.of(
            selectAs(Functions.DURATION_P50, "p50"),
            selectAs(Functions.DURATION_P95, "p95"),
            selectAs(Functions.DURATION_P99, "p99"));
        case "ERROR_RATE" -> List.of(
            selectAs(Functions.INTERACTION_SUCCESS_COUNT, "success_count"),
            selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count"));
        case "USER_CATEGORIES" -> List.of(
            selectAs(Functions.USER_CATEGORY_EXCELLENT, "user_excellent"),
            selectAs(Functions.USER_CATEGORY_GOOD, "user_good"),
            selectAs(Functions.USER_CATEGORY_AVERAGE, "user_avg"),
            selectAs(Functions.USER_CATEGORY_POOR, "user_poor"));
        case "COMPOSITE" -> List.of(
            selectAs(Functions.APDEX, "apdex"),
            selectAs(Functions.INTERACTION_SUCCESS_COUNT, "success_count"),
            selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count"),
            selectAs(Functions.INTERACTION_ERROR_DISTINCT_USERS, "distinct_error_users"),
            selectAs(Functions.DURATION_P50, "p50"),
            selectAs(Functions.DURATION_P95, "p95"),
            selectAs(Functions.DURATION_P99, "p99"),
            selectAs(Functions.FROZEN_FRAME, "frozen_frame"),
            selectAs(Functions.UNANALYSED_FRAME, "unanalysed_frame"),
            selectAs(Functions.ANALYSED_FRAME, "analysed_frame"),
            selectAs(Functions.CRASH, "crash"),
            selectAs(Functions.ANR, "anr"),
            selectAs(Functions.NET_0, "net_0"),
            selectAs(Functions.NET_2XX, "net_2xx"),
            selectAs(Functions.NET_3XX, "net_3xx"),
            selectAs(Functions.NET_4XX, "net_4xx"),
            selectAs(Functions.NET_5XX, "net_5xx"),
            selectAs(Functions.NET_COUNT, "net_count"),
            selectAs(Functions.USER_CATEGORY_EXCELLENT, "user_excellent"),
            selectAs(Functions.USER_CATEGORY_GOOD, "user_good"),
            selectAs(Functions.USER_CATEGORY_AVERAGE, "user_avg"),
            selectAs(Functions.USER_CATEGORY_POOR, "user_poor"),
            selectAs(Functions.ERROR_RATE, "error_rate"),
            selectAs(Functions.CRASH_RATE, "crash_rate"),
            selectAs(Functions.ANR_RATE, "anr_rate"),
            selectAs(Functions.FROZEN_FRAME_RATE, "frozen_frame_rate"),
            selectAs(Functions.POOR_USER_RATE, "poor_user_rate"),
            selectAs(Functions.AVERAGE_USER_RATE, "avg_user_rate"),
            selectAs(Functions.GOOD_USER_RATE, "good_user_rate"),
            selectAs(Functions.EXCELLENT_USER_RATE, "excellent_user_rate"));
        case "FRAMES" -> List.of(
            selectAs(Functions.FROZEN_FRAME, "frozen_frame"),
            selectAs(Functions.UNANALYSED_FRAME, "unanalysed_frame"),
            selectAs(Functions.ANALYSED_FRAME, "analysed_frame"),
            selectAs(Functions.FROZEN_FRAME_RATE, "frozen_frame_rate"));
        case "NETWORK" -> List.of(
            selectAs(Functions.NET_0, "net_0"),
            selectAs(Functions.NET_2XX, "net_2xx"),
            selectAs(Functions.NET_3XX, "net_3xx"),
            selectAs(Functions.NET_4XX, "net_4xx"),
            selectAs(Functions.NET_5XX, "net_5xx"),
            selectAs(Functions.NET_COUNT, "net_count"));
        default -> throw new IllegalArgumentException(
            "Unknown metricType '" + req.getMetricType()
                + "'. Valid: APDEX, LATENCY, ERROR_RATE, USER_CATEGORIES, COMPOSITE, FRAMES, NETWORK");
      };

      List<String> finalSelectItems = new ArrayList<>(selectItems);
      String groupByClause = "";
      String orderByClause = "";
      final String bucketSize = req.isTimeseries() ? computeBucketSize(req.getTimeRange()) : null;

      if (req.isTimeseries()) {
        long bucketSeconds = DateTimeUtils.toSeconds(bucketSize);
        String timeBucketSelect = String.format(Functions.TIME_BUCKET.getChSelectClause(),
            ClickhouseConstants.COL_TIMESTAMP, bucketSeconds, bucketSeconds) + " as t1";
        finalSelectItems.add(0, timeBucketSelect);
        groupByClause = "t1";
        orderByClause = "t1 ASC";
      }

      String selectClause = String.join(",", finalSelectItems);

      // WHERE
      StringBuilder whereClause = buildInteractionWhereClause(req.getTimeRange());
      appendSpanNameFilter(whereClause, req.getInteractionName());
      appendUserFilters(whereClause, req.getFilters());

      // Build query
      StringBuilder query = new StringBuilder(
          String.format("Select %s from %s where %s",
              selectClause, ClickhouseConstants.OTEL_TRACES_TABLE, whereClause));
      if (!StringUtils.isEmpty(groupByClause)) {
        query.append(String.format(" group by %s", groupByClause));
      }
      if (!StringUtils.isEmpty(orderByClause)) {
        query.append(String.format(" order by %s", orderByClause));
      }

      return clickhouseQueryService.executeQueryOrCreateJob(QueryConfiguration.newQuery(query.toString())
              .timeoutMs(ClickhouseConstants.DEFAULT_QUERY_TIMEOUT_MS)
              .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
              .projectId(req.getProjectId())
              .build())
          .map(rawRes -> {
            List<String> fields = extractFieldNames(rawRes);

            if (req.isTimeseries()) {
              List<InteractionTimeseriesRow> tsRows
                  = rawRes.data.getRows().stream().map(row -> {
                List<Object> values = extractRowValues(row);
                return InteractionTimeseriesRow.builder()
                    .timestamp(parseString(values, fields, "t1"))
                    .apdex(parseDouble(values, fields, "apdex"))
                    .successCount(parseLong(values, fields, "success_count"))
                    .errorCount(parseLong(values, fields, "error_count"))
                    .distinctErrorUsers(parseLong(values, fields, "distinct_error_users"))
                    .p50Ms(parseDouble(values, fields, "p50"))
                    .p95Ms(parseDouble(values, fields, "p95"))
                    .p99Ms(parseDouble(values, fields, "p99"))
                    .frozenFrame(parseLong(values, fields, "frozen_frame"))
                    .unanalysedFrame(parseLong(values, fields, "unanalysed_frame"))
                    .analysedFrame(parseLong(values, fields, "analysed_frame"))
                    .crash(parseLong(values, fields, "crash"))
                    .anr(parseLong(values, fields, "anr"))
                    .net0(parseLong(values, fields, "net_0"))
                    .net2xx(parseLong(values, fields, "net_2xx"))
                    .net3xx(parseLong(values, fields, "net_3xx"))
                    .net4xx(parseLong(values, fields, "net_4xx"))
                    .net5xx(parseLong(values, fields, "net_5xx"))
                    .netCount(parseLong(values, fields, "net_count"))
                    .userExcellent(parseLong(values, fields, "user_excellent"))
                    .userGood(parseLong(values, fields, "user_good"))
                    .userAverage(parseLong(values, fields, "user_avg"))
                    .userPoor(parseLong(values, fields, "user_poor"))
                    .errorRatePct(parseDouble(values, fields, "error_rate"))
                    .crashRatePct(parseDouble(values, fields, "crash_rate"))
                    .anrRatePct(parseDouble(values, fields, "anr_rate"))
                    .frozenFrameRatePct(parseDouble(values, fields, "frozen_frame_rate"))
                    .poorUserRatePct(parseDouble(values, fields, "poor_user_rate"))
                    .avgUserRatePct(parseDouble(values, fields, "avg_user_rate"))
                    .goodUserRatePct(parseDouble(values, fields, "good_user_rate"))
                    .excellentUserRatePct(parseDouble(values, fields, "excellent_user_rate"))
                    .build();
              }).toList();
              return InteractionMetricsRes.builder()
                  .bucketSize(bucketSize)
                  .timeseries(tsRows)
                  .build();
            } else {
              // Aggregate — single row
              if (rawRes.data.getRows().isEmpty()) {
                return InteractionMetricsRes.builder()
                    .metrics(InteractionMetricsRow.builder().build())
                    .build();
              }
              List<Object> values = extractRowValues(rawRes.data.getRows().get(0));
              InteractionMetricsRow metricsRow =
                  InteractionMetricsRow.builder()
                      .apdex(parseDouble(values, fields, "apdex"))
                      .p50Ms(parseDouble(values, fields, "p50"))
                      .p95Ms(parseDouble(values, fields, "p95"))
                      .p99Ms(parseDouble(values, fields, "p99"))
                      .successCount(parseLong(values, fields, "success_count"))
                      .errorCount(parseLong(values, fields, "error_count"))
                      .distinctErrorUsers(parseLong(values, fields, "distinct_error_users"))
                      .frozenFrame(parseLong(values, fields, "frozen_frame"))
                      .analysedFrame(parseLong(values, fields, "analysed_frame"))
                      .unanalysedFrame(parseLong(values, fields, "unanalysed_frame"))
                      .crash(parseLong(values, fields, "crash"))
                      .anr(parseLong(values, fields, "anr"))
                      .net0(parseLong(values, fields, "net_0"))
                      .net2xx(parseLong(values, fields, "net_2xx"))
                      .net3xx(parseLong(values, fields, "net_3xx"))
                      .net4xx(parseLong(values, fields, "net_4xx"))
                      .net5xx(parseLong(values, fields, "net_5xx"))
                      .netCount(parseLong(values, fields, "net_count"))
                      .userExcellent(parseLong(values, fields, "user_excellent"))
                      .userGood(parseLong(values, fields, "user_good"))
                      .userAverage(parseLong(values, fields, "user_avg"))
                      .userPoor(parseLong(values, fields, "user_poor"))
                      .errorRatePct(parseDouble(values, fields, "error_rate"))
                      .crashRatePct(parseDouble(values, fields, "crash_rate"))
                      .anrRatePct(parseDouble(values, fields, "anr_rate"))
                      .frozenFrameRatePct(parseDouble(values, fields, "frozen_frame_rate"))
                      .poorUserRatePct(parseDouble(values, fields, "poor_user_rate"))
                      .avgUserRatePct(parseDouble(values, fields, "avg_user_rate"))
                      .goodUserRatePct(parseDouble(values, fields, "good_user_rate"))
                      .excellentUserRatePct(parseDouble(values, fields, "excellent_user_rate"))
                      .build();
              return InteractionMetricsRes.builder()
                  .metrics(metricsRow)
                  .build();
            }
          });
    });
  }

  @Override
  public Single<InteractionBreakdownRes> getInteractionBreakdown(
      InteractionBreakdownReq req) {
    return Single.defer(() -> {
      if (req.getDimension() == null) {
        throw new IllegalArgumentException("dimension is required");
      }
      String rawDimension = req.getDimension();
      String dimensionKey = rawDimension.toLowerCase();

      // DIMENSION_CONFIG — each dimension maps a ClickHouse column to a fixed set of metrics
      DimensionConfig config = switch (dimensionKey) {
        case "region" -> new DimensionConfig(ClickhouseConstants.COL_GEO_STATE, "region", List.of(
            selectAs(Functions.INTERACTION_SUCCESS_COUNT, "success_count"),
            selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count"),
            selectAs(Functions.USER_CATEGORY_POOR, "user_poor")));
        case "device" -> new DimensionConfig(ClickhouseConstants.COL_DEVICE_MODEL, "deviceModel", List.of(
            selectAs(Functions.FROZEN_FRAME, "frozen_frame"),
            selectAs(Functions.ANR, "anr"),
            selectAs(Functions.CRASH, "crash")));
        case "release" -> new DimensionConfig(ClickhouseConstants.COL_APP_VERSION, "release", List.of(
            selectAs(Functions.APDEX, "apdex"),
            selectAs(Functions.CRASH, "crash"),
            selectAs(Functions.ANR, "anr"),
            selectAs(Functions.INTERACTION_SUCCESS_COUNT, "success_count"),
            selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count")));
        case "platform" -> new DimensionConfig(ClickhouseConstants.COL_PLATFORM, "platform", List.of(
            selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count"),
            selectAs(Functions.USER_CATEGORY_POOR, "user_poor")));
        case "os" -> new DimensionConfig(ClickhouseConstants.COL_OS_VERSION, "os_version", List.of(
            selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count"),
            selectAs(Functions.USER_CATEGORY_POOR, "user_poor")));
        case "network" -> new DimensionConfig(ClickhouseConstants.COL_NETWORK_PROVIDER, "network", List.of(
            selectAs(Functions.INTERACTION_SUCCESS_COUNT, "success_count"),
            selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count")));
        case "latency_by_network" -> new DimensionConfig(ClickhouseConstants.COL_NETWORK_PROVIDER, "network", List.of(
            selectAs(Functions.DURATION_P50, "p50"),
            selectAs(Functions.DURATION_P95, "p95"),
            selectAs(Functions.DURATION_P99, "p99")));
        case "latency_by_device" -> new DimensionConfig(ClickhouseConstants.COL_DEVICE_MODEL, "deviceModel", List.of(
            selectAs(Functions.DURATION_P50, "p50"),
            selectAs(Functions.DURATION_P95, "p95"),
            selectAs(Functions.DURATION_P99, "p99")));
        case "latency_by_os" -> new DimensionConfig(ClickhouseConstants.COL_OS_VERSION, "os_version", List.of(
            selectAs(Functions.DURATION_P50, "p50"),
            selectAs(Functions.DURATION_P95, "p95"),
            selectAs(Functions.DURATION_P99, "p99")));
        default -> throw new IllegalArgumentException(
            "Unknown breakdown dimension '" + rawDimension + "'. Valid values: region, device, release, platform, os, network, latency_by_network, latency_by_device, latency_by_os");
      };

      // Build select: metric columns + dimension column
      List<String> selectItems = new ArrayList<>(config.selectItems());
      selectItems.add(selectColumn(config.columnName(), config.alias()));
      String selectClause = String.join(",", selectItems);

      // WHERE
      StringBuilder whereClause = buildInteractionWhereClause(req.getTimeRange());
      appendSpanNameFilter(whereClause, req.getInteractionName());
      appendUserFilters(whereClause, req.getFilters());

      int limit = Objects.requireNonNullElse(req.getLimit(), ClickhouseConstants.DEFAULT_INTERACTION_LIMIT);
      String breakdownOrderBy = buildOrderByClause(req.getOrderBy(), null);

      StringBuilder queryBuilder = new StringBuilder(
          String.format("Select %s from %s where %s group by %s",
              selectClause, ClickhouseConstants.OTEL_TRACES_TABLE, whereClause, config.alias()));
      if (!StringUtils.isEmpty(breakdownOrderBy)) {
        queryBuilder.append(String.format(" order by %s", breakdownOrderBy));
      }
      queryBuilder.append(String.format(" limit %d", limit));
      String query = queryBuilder.toString();

      return clickhouseQueryService.executeQueryOrCreateJob(QueryConfiguration.newQuery(query)
              .timeoutMs(ClickhouseConstants.DEFAULT_QUERY_TIMEOUT_MS)
              .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
              .projectId(req.getProjectId())
              .build())
          .map(rawRes -> {
            List<String> fields = extractFieldNames(rawRes);
            List<Map<String, Object>> breakdownRows = rawRes.data.getRows().stream().map(row -> {
              List<Object> values = extractRowValues(row);
              Map<String, Object> rowMap = new LinkedHashMap<>();
              for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);
                Object rawValue = i < values.size() ? values.get(i) : null;
                // Attempt numeric parsing for metric values; keep strings for dimension values
                if (rawValue != null) {
                  String strVal = rawValue.toString();
                  try {
                    if (strVal.contains(".")) {
                      rowMap.put(field, Double.parseDouble(strVal));
                    } else {
                      rowMap.put(field, Long.parseLong(strVal));
                    }
                  } catch (NumberFormatException e) {
                    rowMap.put(field, strVal);
                  }
                } else {
                  rowMap.put(field, null);
                }
              }
              return rowMap;
            }).toList();
            return InteractionBreakdownRes.builder()
                .dimension(rawDimension)
                .breakdown(breakdownRows)
                .build();
          });
    });
  }

  @Override
  public Single<InteractionSessionsRes> getInteractionSessions(
      InteractionSessionsReq req) {
    return Single.defer(() -> {
      if (req.getScope() == null) {
        throw new IllegalArgumentException("scope is required");
      }
      String scope = req.getScope().toLowerCase();
      if (!scope.equals("sessions") && !scope.equals("stats")) {
        throw new IllegalArgumentException(
            "Unknown scope '" + req.getScope() + "'. Valid: sessions, stats");
      }

      // WHERE
      StringBuilder whereClause = buildInteractionWhereClause(req.getTimeRange());
      appendSpanNameFilter(whereClause, req.getInteractionName());
      appendEventTypeFilter(whereClause, req.getEventType());
      appendUserFilters(whereClause, req.getFilters());

      String selectClause;
      String orderByClause = "";
      Integer limit = null;

      if (scope.equals("sessions")) {
        selectClause = String.join(",", List.of(
            selectColumn(ClickhouseConstants.COL_TIMESTAMP, "timestamp"),
            selectColumn(ClickhouseConstants.COL_DURATION, "duration"),
            selectColumn(ClickhouseConstants.COL_TRACE_ID, "trace_id"),
            selectColumn(ClickhouseConstants.COL_SPAN_ID, "span_id"),
            selectColumn(ClickhouseConstants.COL_STATUS_CODE, "status_code"),
            selectColumn(ClickhouseConstants.COL_PLATFORM, "platform"),
            selectColumn(ClickhouseConstants.COL_DEVICE_MODEL, "device"),
            selectColumn(ClickhouseConstants.COL_OS_VERSION, "os_version"),
            selectColumn(ClickhouseConstants.COL_APP_VERSION, "app_version"),
            selectColumn("UserId", "user_id"),
            selectColumn("SessionId", "session_id")
        ));
        orderByClause = buildOrderByClause(req.getOrderBy(), "timestamp DESC");
        limit = Objects.requireNonNullElse(req.getLimit(), ClickhouseConstants.DEFAULT_INTERACTION_LIMIT);
      } else {
        selectClause = String.join(",", List.of(
            "COUNT() as total_sessions",
            selectAs(Functions.INTERACTION_SUCCESS_COUNT, "success_count"),
            selectAs(Functions.INTERACTION_ERROR_COUNT, "error_count"),
            selectAs(Functions.CRASH, "crash"),
            selectAs(Functions.ANR, "anr"),
            selectAs(Functions.APDEX, "apdex"),
            selectAs(Functions.DURATION_P50, "p50"),
            selectAs(Functions.DURATION_P95, "p95"),
            selectAs(Functions.DURATION_P99, "p99"),
            selectAs(Functions.INTERACTION_ERROR_DISTINCT_USERS, "distinct_error_users")
        ));
      }

      // Build query
      StringBuilder query = new StringBuilder(
          String.format("Select %s from %s where %s",
              selectClause, ClickhouseConstants.OTEL_TRACES_TABLE, whereClause));
      if (!StringUtils.isEmpty(orderByClause)) {
        query.append(String.format(" order by %s", orderByClause));
      }
      if (limit != null) {
        query.append(String.format(" limit %d", limit));
      }

      String finalScope = scope;
      return clickhouseQueryService.executeQueryOrCreateJob(QueryConfiguration.newQuery(query.toString())
              .timeoutMs(ClickhouseConstants.DEFAULT_QUERY_TIMEOUT_MS)
              .jobCreationMode(JobCreationMode.JOB_CREATION_OPTIONAL)
              .projectId(req.getProjectId())
              .build())
          .map(rawRes -> {
            List<String> fields = extractFieldNames(rawRes);

            if (finalScope.equals("sessions")) {
              List<InteractionSessionRow> sessionRows
                  = rawRes.data.getRows().stream().map(row -> {
                List<Object> values = extractRowValues(row);
                return InteractionSessionRow.builder()
                    .traceId(parseString(values, fields, "trace_id"))
                    .spanId(parseString(values, fields, "span_id"))
                    .timestamp(parseString(values, fields, "timestamp"))
                    .durationMs(parseLong(values, fields, "duration"))
                    .statusCode(parseString(values, fields, "status_code"))
                    .platform(parseString(values, fields, "platform"))
                    .deviceModel(parseString(values, fields, "device"))
                    .osVersion(parseString(values, fields, "os_version"))
                    .appVersion(parseString(values, fields, "app_version"))
                    .userId(parseString(values, fields, "user_id"))
                    .sessionId(parseString(values, fields, "session_id"))
                    .build();
              }).toList();
              return InteractionSessionsRes.builder()
                  .sessions(sessionRows)
                  .build();
            } else {
              // Stats scope — single aggregate row
              if (rawRes.data.getRows().isEmpty()) {
                return InteractionSessionsRes.builder()
                    .stats(InteractionSessionStatsRow.builder().build())
                    .build();
              }
              List<Object> values = extractRowValues(rawRes.data.getRows().get(0));
              InteractionSessionStatsRow statsRow =
                  InteractionSessionStatsRow.builder()
                      .totalSessions(parseLong(values, fields, "total_sessions"))
                      .successCount(parseLong(values, fields, "success_count"))
                      .errorCount(parseLong(values, fields, "error_count"))
                      .crash(parseLong(values, fields, "crash"))
                      .anr(parseLong(values, fields, "anr"))
                      .apdex(parseDouble(values, fields, "apdex"))
                      .p50Ms(parseDouble(values, fields, "p50"))
                      .p95Ms(parseDouble(values, fields, "p95"))
                      .p99Ms(parseDouble(values, fields, "p99"))
                      .distinctErrorUsers(parseLong(values, fields, "distinct_error_users"))
                      .build();
              return InteractionSessionsRes.builder()
                  .stats(statsRow)
                  .build();
            }
          });
    });
  }

  // ---------------------------------------------------------------------------
  // Query building helpers
  // ---------------------------------------------------------------------------

  /** Formats a Functions enum as "SQL_EXPRESSION as alias". */
  private String selectAs(Functions function, String alias) {
    return String.format("%s as %s", function.getChSelectClause(), alias);
  }

  /** Formats a column reference as "COLUMN as alias". */
  private String selectColumn(String columnName, String alias) {
    return String.format("%s as %s", columnName, alias);
  }

  /** Builds the base WHERE clause with time range + PulseType='interaction' injection. */
  private StringBuilder buildInteractionWhereClause(
      TimeRange timeRange) {
    StringBuilder whereClause;
    if (timeRange == null || timeRange.getStart() == null || timeRange.getEnd() == null) {
      whereClause = new StringBuilder("1=1");
    } else {
      whereClause = new StringBuilder(String.format(
          "Timestamp >= toDateTime64('%s',9,'UTC') AND Timestamp <= toDateTime64('%s',9,'UTC')",
          ZonedDateTime.parse(timeRange.getStart()).format(output),
          ZonedDateTime.parse(timeRange.getEnd()).format(output)));
    }
    whereClause.append(ClickhouseConstants.INTERACTION_PULSE_TYPE_FILTER);
    return whereClause;
  }

  /** Escapes single quotes in a SQL string literal to prevent injection. */
  private static String escapeSql(String value) {
    return value == null ? null : value.replace("'", "''");
  }

  /** Whitelist of SQL aliases that are valid ORDER BY targets across all interaction endpoints. */
  private static final java.util.Set<String> VALID_ORDER_BY_FIELDS = java.util.Set.of(
      // health
      ClickhouseConstants.ALIAS_SPAN_FREQ, "apdex", "success_count", "error_count", "p50",
      "interaction_name", "user_excellent", "user_good", "user_avg", "user_poor",
      // breakdown metrics (p95/p99/crash/etc. not in health but valid in breakdown)
      "p95", "p99", "frozen_frame", "anr", "crash",
      // breakdown dimension aliases
      "region", "deviceModel", "release", "platform", "os_version", "network",
      // sessions list
      "timestamp", "duration", "durationMs", "trace_id", "span_id", "status_code",
      "device", "app_version", "user_id", "session_id",
      // sessions stats
      "total_sessions", "distinct_error_users"
  );

  /**
   * Builds an ORDER BY clause from a list of InteractionOrderBy entries.
   * Fields are SQL aliases (repo convention). Validated against a whitelist to prevent injection.
   * Validates direction is ASC or DESC. Returns {@code fallback} when the list is null or empty.
   */
  private String buildOrderByClause(
      List<InteractionOrderBy> orderBy,
      String fallback) {
    if (CollectionUtils.isEmpty(orderBy)) return fallback;
    return orderBy.stream().map(o -> {
      if (!VALID_ORDER_BY_FIELDS.contains(o.getField())) {
        throw new IllegalArgumentException(
            "Unknown orderBy field '" + o.getField() + "'. Valid fields: " + VALID_ORDER_BY_FIELDS);
      }
      String dir = o.getDirection().toUpperCase();
      if (!dir.equals("ASC") && !dir.equals("DESC")) {
        throw new IllegalArgumentException("Invalid orderBy direction: " + o.getDirection());
      }
      return o.getField() + " " + dir;
    }).collect(Collectors.joining(", "));
  }

  /** Appends SpanName IN filter for a single interaction name. */
  private void appendSpanNameFilter(StringBuilder whereClause, String interactionName) {
    if (StringUtils.isNotBlank(interactionName)) {
      whereClause.append(String.format(" And %s In ('%s')",
          ClickhouseConstants.COL_SPAN_NAME, escapeSql(interactionName)));
    }
  }

  /** Appends SpanName IN filter for multiple interaction names. */
  private void appendInteractionNamesFilter(StringBuilder whereClause, List<String> interactionNames) {
    if (!CollectionUtils.isEmpty(interactionNames)) {
      String names = interactionNames.stream()
          .map(name -> String.format("'%s'", escapeSql(name)))
          .collect(Collectors.joining(","));
      whereClause.append(String.format(" And %s In (%s)", ClickhouseConstants.COL_SPAN_NAME, names));
    }
  }

  /** Appends event type filter using EVENT_TYPE_MAP. */
  private void appendEventTypeFilter(StringBuilder whereClause, String eventType) {
    if (StringUtils.isNotBlank(eventType)) {
      String clickhouseEventName = EVENT_TYPE_MAP.getOrDefault(eventType, eventType);
      whereClause.append(String.format(" And (has(Events.Name, '%s'))", escapeSql(clickhouseEventName)));
    }
  }

  /** Appends user filters (Map<String,String>) using FILTER_COLUMN_MAP for key translation. */
  private void appendUserFilters(StringBuilder whereClause, java.util.Map<String, String> filters) {
    if (filters == null || filters.isEmpty()) {
      return;
    }
    for (java.util.Map.Entry<String, String> entry : filters.entrySet()) {
      String columnName = FILTER_COLUMN_MAP.get(entry.getKey().toLowerCase());
      if (columnName == null) {
        throw new IllegalArgumentException(
            "Unknown filter key '" + entry.getKey() + "'. Valid keys: " + FILTER_COLUMN_MAP.keySet());
      }
      whereClause.append(String.format(" And %s In ('%s')", columnName, escapeSql(entry.getValue())));
    }
  }

  /** Computes optimal time bucket size based on the time range span. */
  private String computeBucketSize(
      TimeRange timeRange) {
    if (timeRange == null || timeRange.getStart() == null || timeRange.getEnd() == null) {
      return "1h";
    }
    try {
      long startEpoch = ZonedDateTime.parse(timeRange.getStart()).toEpochSecond();
      long endEpoch = ZonedDateTime.parse(timeRange.getEnd()).toEpochSecond();
      long spanSeconds = endEpoch - startEpoch;
      if (spanSeconds <= 3600) return "5m";
      if (spanSeconds <= 86400) return "1h";
      if (spanSeconds <= 604800) return "12h";
      return "1d";
    } catch (Exception e) {
      return "1h";
    }
  }

  // ---------------------------------------------------------------------------
  // Response parsing helpers
  // ---------------------------------------------------------------------------

  /** Extracts field names from a ClickHouse response. */
  private List<String> extractFieldNames(
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    return response.data.getSchema().getFields().stream()
        .map(GetRawUserEventsResponseDto.Field::getName).toList();
  }

  /** Extracts raw values from a ClickHouse result row. */
  private List<Object> extractRowValues(GetRawUserEventsResponseDto.Row row) {
    return row.getRowFields().stream()
        .map(GetRawUserEventsResponseDto.RowField::getValue).toList();
  }

  /** Safely parses a String value from a result row by column alias. */
  private String parseString(List<Object> values, List<String> fields, String alias) {
    int index = fields.indexOf(alias);
    if (index < 0 || index >= values.size() || values.get(index) == null) return null;
    return values.get(index).toString();
  }

  /** Safely parses a Long value from a result row by column alias. Returns null if absent. */
  private Long parseLong(List<Object> values, List<String> fields, String alias) {
    String raw = parseString(values, fields, alias);
    if (raw == null || raw.isEmpty()) return null;
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      try {
        return (long) Double.parseDouble(raw);
      } catch (Exception parseException) {
        return null;
      }
    }
  }

  /** Safely parses a Double value from a result row by column alias. */
  private Double parseDouble(List<Object> values, List<String> fields, String alias) {
    String raw = parseString(values, fields, alias);
    if (raw == null || raw.isEmpty()) return null;
    try {
      return Double.parseDouble(raw);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Computes percentage rate from numerator/denominator. */
  private Double computeRate(Long numerator, Long denominator) {
    if (denominator == null || denominator == 0) return null;
    return (numerator == null ? 0.0 : (double) numerator / denominator) * 100.0;
  }
}

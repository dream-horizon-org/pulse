package org.dreamhorizon.pulseserver.service.interaction;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.model.JobCreationMode;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.mapstruct.ap.internal.util.Strings;

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
          // Network metrics for alerts (uses SpanType)
          case NET_0_BY_SPAN_TYPE -> String.format("%s as %s", Functions.NET_0_BY_SPAN_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_0_BY_SPAN_TYPE.getDisplayName()));
          case NET_2XX_BY_SPAN_TYPE -> String.format("%s as %s", Functions.NET_2XX_BY_SPAN_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_2XX_BY_SPAN_TYPE.getDisplayName()));
          case NET_3XX_BY_SPAN_TYPE -> String.format("%s as %s", Functions.NET_3XX_BY_SPAN_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_3XX_BY_SPAN_TYPE.getDisplayName()));
          case NET_4XX_BY_SPAN_TYPE -> String.format("%s as %s", Functions.NET_4XX_BY_SPAN_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_4XX_BY_SPAN_TYPE.getDisplayName()));
          case NET_5XX_BY_SPAN_TYPE -> String.format("%s as %s", Functions.NET_5XX_BY_SPAN_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_5XX_BY_SPAN_TYPE.getDisplayName()));
          case NET_COUNT_BY_SPAN_TYPE -> String.format("%s as %s", Functions.NET_COUNT_BY_SPAN_TYPE.getChSelectClause(),
              Objects.requireNonNullElse(selectItem.getAlias(), Functions.NET_COUNT_BY_SPAN_TYPE.getDisplayName()));
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
    if (!Strings.isEmpty(groupByClause)) {
      query += String.format(" group by %s", groupByClause);
    }
    if (!Strings.isEmpty(orderByClause)) {
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
}

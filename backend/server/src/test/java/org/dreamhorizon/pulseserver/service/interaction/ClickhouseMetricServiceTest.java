package org.dreamhorizon.pulseserver.service.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.ArrayList;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClickhouseMetricServiceTest {

  @Mock
  ClickhouseQueryService clickhouseQueryService;

  ClickhouseMetricService clickhouseMetricService;

  @BeforeEach
  void setUp() {
    clickhouseMetricService = new ClickhouseMetricService(clickhouseQueryService);
  }

  private QueryRequest createBasicRequest() {
    QueryRequest request = new QueryRequest();
    QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
    timeRange.setStart("2024-01-01T00:00:00Z");
    timeRange.setEnd("2024-01-01T23:59:59Z");
    request.setTimeRange(timeRange);
    request.setDataType(QueryRequest.DataType.TRACES);
    request.setProjectId("proj-123");
    return request;
  }

  private GetQueryDataResponseDto<GetRawUserEventsResponseDto> createMockResponse(
      List<String> fieldNames, List<List<Object>> rowData) {
    List<GetRawUserEventsResponseDto.Field> fields = fieldNames.stream()
        .map(GetRawUserEventsResponseDto.Field::new)
        .toList();

    List<GetRawUserEventsResponseDto.Row> rows = rowData.stream()
        .map(row -> {
          List<GetRawUserEventsResponseDto.RowField> rowFields = row.stream()
              .map(GetRawUserEventsResponseDto.RowField::new)
              .toList();
          return new GetRawUserEventsResponseDto.Row(rowFields);
        })
        .toList();

    GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(fields);
    GetRawUserEventsResponseDto data = GetRawUserEventsResponseDto.builder()
        .schema(schema)
        .rows(rows)
        .build();

    return GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
        .data(data)
        .jobComplete(true)
        .build();
  }

  @Nested
  class GetMetricDistribution {

    @Test
    void shouldReturnMetricDistributionSuccessfully() {
      QueryRequest request = createBasicRequest();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("apdex", "crash"),
          List.of(List.of("0.95", "0")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      PerformanceMetricDistributionRes result =
          clickhouseMetricService.getMetricDistribution(request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getFields()).containsExactly("apdex", "crash");
      assertThat(result.getRows()).hasSize(1);
      assertThat(result.getRows().get(0)).containsExactly("0.95", "0");

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getProjectId()).isEqualTo("proj-123");
    }

    @Test
    void shouldReturnEmptyRowsWhenResponseHasNoData() {
      QueryRequest request = createBasicRequest();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("field1"),
          List.of(List.of("value1")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      PerformanceMetricDistributionRes result =
          clickhouseMetricService.getMetricDistribution(request).blockingGet();

      assertThat(result.getRows()).hasSize(1);
      assertThat(result.getRows().get(0)).hasSize(1);
    }

    @Test
    void shouldPropagateErrorFromClickhouseQueryService() {
      QueryRequest request = createBasicRequest();
      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.error(new RuntimeException("ClickHouse unavailable")));

      TestObserver<PerformanceMetricDistributionRes> observer =
          clickhouseMetricService.getMetricDistribution(request).test();

      observer.assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("ClickHouse unavailable"));
    }

    @Test
    void shouldUseExceptionsTableForExceptionsDataType() {
      QueryRequest request = createBasicRequest();
      request.setDataType(QueryRequest.DataType.EXCEPTIONS);
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("error_count"),
          List.of(List.of("5")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("stack_trace_events");
    }

    @Test
    void shouldUseLogsTableForLogsDataType() {
      QueryRequest request = createBasicRequest();
      request.setDataType(QueryRequest.DataType.LOGS);
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("count"),
          List.of(List.of("10")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("otel_logs");
    }

    @Test
    void shouldUseMetricsTableForMetricsDataType() {
      QueryRequest request = createBasicRequest();
      request.setDataType(QueryRequest.DataType.METRICS);
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("metric_value"),
          List.of(List.of("42")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("otel_metrics");
    }

    @Test
    void shouldBuildQueryWithFiltersLikeInEq() {
      QueryRequest request = createBasicRequest();
      request.setFilters(new ArrayList<>());

      QueryRequest.Filter likeFilter = new QueryRequest.Filter();
      likeFilter.setField("ScreenName");
      likeFilter.setOperator(QueryRequest.Operator.LIKE);
      likeFilter.setValue(List.of("Home%"));
      request.getFilters().add(likeFilter);

      QueryRequest.Filter inFilter = new QueryRequest.Filter();
      inFilter.setField("Status");
      inFilter.setOperator(QueryRequest.Operator.IN);
      inFilter.setValue(List.of("ok", "pending"));
      request.getFilters().add(inFilter);

      QueryRequest.Filter eqFilter = new QueryRequest.Filter();
      eqFilter.setField("Code");
      eqFilter.setOperator(QueryRequest.Operator.EQ);
      eqFilter.setValue(List.of("200"));
      request.getFilters().add(eqFilter);

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("count"),
          List.of(List.of("1")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      PerformanceMetricDistributionRes result =
          clickhouseMetricService.getMetricDistribution(request).blockingGet();

      assertThat(result).isNotNull();
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      assertThat(query).contains("like");
      assertThat(query).contains("In");
      assertThat(query).contains("ScreenName");
      assertThat(query).contains("Status");
      assertThat(query).contains("Code");
    }

    @Test
    void shouldBuildQueryWithGroupByAndOrderBy() {
      QueryRequest request = createBasicRequest();
      request.setGroupBy(List.of("ScreenName", "Date"));
      request.setOrderBy(List.of(
          createOrderBy("apdex", QueryRequest.Direction.DESC),
          createOrderBy("Date", QueryRequest.Direction.ASC)));

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("ScreenName", "Date", "apdex"),
          List.of(List.of("Home", "2024-01-01", "0.95")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      PerformanceMetricDistributionRes result =
          clickhouseMetricService.getMetricDistribution(request).blockingGet();

      assertThat(result).isNotNull();
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      assertThat(query).contains("group by");
      assertThat(query).contains("ScreenName");
      assertThat(query).contains("Date");
      assertThat(query).contains("order by");
      assertThat(query).contains("DESC");
      assertThat(query).contains("ASC");
    }

    @Test
    void shouldBuildQueryWithSelectFunctionsAndCustomLimit() {
      QueryRequest request = createBasicRequest();
      request.setLimit(50);

      QueryRequest.SelectItem apdexSelect = new QueryRequest.SelectItem();
      apdexSelect.setFunction(Functions.APDEX);
      apdexSelect.setAlias("apdex_score");

      QueryRequest.SelectItem crashSelect = new QueryRequest.SelectItem();
      crashSelect.setFunction(Functions.CRASH);

      request.setSelect(List.of(apdexSelect, crashSelect));

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("apdex_score", "crash"),
          List.of(List.of("0.92", "0")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      PerformanceMetricDistributionRes result =
          clickhouseMetricService.getMetricDistribution(request).blockingGet();

      assertThat(result.getFields()).containsExactly("apdex_score", "crash");
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("limit 50");
    }

    @Test
    void shouldConvertNullRowValuesToEmptyString() {
      QueryRequest request = createBasicRequest();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("a", "b"),
          List.of(java.util.Arrays.asList("val", null)));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      PerformanceMetricDistributionRes result =
          clickhouseMetricService.getMetricDistribution(request).blockingGet();

      assertThat(result.getRows()).hasSize(1);
      assertThat(result.getRows().get(0)).containsExactly("val", "");
    }

    @Test
    void shouldUseAdditionalFilterOperator() {
      QueryRequest request = createBasicRequest();
      request.setFilters(new ArrayList<>());
      QueryRequest.Filter additionalFilter = new QueryRequest.Filter();
      additionalFilter.setOperator(QueryRequest.Operator.ADDITIONAL);
      additionalFilter.setValue(List.of("(custom_expr = 1)"));
      request.getFilters().add(additionalFilter);

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
          List.of("x"),
          List.of(List.of("1")));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(mockResponse));

      clickhouseMetricService.getMetricDistribution(request).blockingGet();

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("custom_expr = 1");
    }
  }

  @Nested
  class SelectFunctions {

    @Test
    void shouldBuildSelectWithColFunction() {
      QueryRequest request = createBasicRequest();
      QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
      selectItem.setFunction(Functions.COL);
      selectItem.setParam(java.util.Map.of("field", "ServiceName"));
      request.setSelect(List.of(selectItem));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(createMockResponse(List.of("ServiceName"), List.of(List.of("api")))));

      clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("ServiceName");
    }

    @Test
    void shouldBuildSelectWithCustomFunction() {
      QueryRequest request = createBasicRequest();
      QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
      selectItem.setFunction(Functions.CUSTOM);
      selectItem.setParam(java.util.Map.of("expression", "count(*)"));
      request.setSelect(List.of(selectItem));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(createMockResponse(List.of("count"), List.of(List.of("10")))));

      clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("count(*)");
    }

    @Test
    void shouldBuildSelectWithTimeBucketFunction() {
      QueryRequest request = createBasicRequest();
      QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
      selectItem.setFunction(Functions.TIME_BUCKET);
      selectItem.setParam(java.util.Map.of("field", "Timestamp", "bucket", "1h"));
      request.setSelect(List.of(selectItem));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(createMockResponse(List.of("bucket"), List.of(List.of("2024-01-01 00:00:00")))));

      clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("Timestamp");
    }

    @Test
    void shouldBuildSelectWithArrToStrFunction() {
      QueryRequest request = createBasicRequest();
      QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
      selectItem.setFunction(Functions.ARR_TO_STR);
      selectItem.setParam(java.util.Map.of("field", "Tags"));
      request.setSelect(List.of(selectItem));

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(createMockResponse(List.of("Tags"), List.of(List.of("a,b,c")))));

      clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getQuery()).contains("Tags");
    }

    @Test
    void shouldBuildSelectWithAllRemainingFunctions() {
      Functions[] functionsToTest = {
          Functions.ANALYSED_FRAME,
          Functions.UNANALYSED_FRAME,
          Functions.DURATION_P99,
          Functions.DURATION_P50,
          Functions.DURATION_P95,
          Functions.INTERACTION_SUCCESS_COUNT,
          Functions.INTERACTION_ERROR_COUNT,
          Functions.INTERACTION_ERROR_DISTINCT_USERS,
          Functions.USER_CATEGORY_AVERAGE,
          Functions.USER_CATEGORY_GOOD,
          Functions.USER_CATEGORY_POOR,
          Functions.USER_CATEGORY_EXCELLENT,
          Functions.NET_0,
          Functions.NET_2XX,
          Functions.NET_3XX,
          Functions.NET_4XX,
          Functions.NET_5XX,
          Functions.NET_COUNT,
          Functions.CRASH_RATE,
          Functions.ANR_RATE,
          Functions.FROZEN_FRAME_RATE,
          Functions.ERROR_RATE,
          Functions.POOR_USER_RATE,
          Functions.AVERAGE_USER_RATE,
          Functions.GOOD_USER_RATE,
          Functions.EXCELLENT_USER_RATE,
          Functions.LOAD_TIME,
          Functions.SCREEN_TIME,
          Functions.SCREEN_DAILY_USERS,
          Functions.NET_4XX_RATE,
          Functions.NET_5XX_RATE,
          Functions.NET_0_BY_PULSE_TYPE,
          Functions.NET_2XX_BY_PULSE_TYPE,
          Functions.NET_3XX_BY_PULSE_TYPE,
          Functions.NET_4XX_BY_PULSE_TYPE,
          Functions.NET_5XX_BY_PULSE_TYPE,
          Functions.NET_COUNT_BY_PULSE_TYPE,
          Functions.CRASH_FREE_USERS_PERCENTAGE,
          Functions.CRASH_FREE_SESSIONS_PERCENTAGE,
          Functions.CRASH_USERS,
          Functions.CRASH_SESSIONS,
          Functions.ALL_USERS,
          Functions.ALL_SESSIONS,
          Functions.ANR_FREE_USERS_PERCENTAGE,
          Functions.ANR_FREE_SESSIONS_PERCENTAGE,
          Functions.ANR_USERS,
          Functions.ANR_SESSIONS,
          Functions.NON_FATAL_FREE_USERS_PERCENTAGE,
          Functions.NON_FATAL_FREE_SESSIONS_PERCENTAGE,
          Functions.NON_FATAL_USERS,
          Functions.NON_FATAL_SESSIONS
      };

      when(clickhouseQueryService.executeQueryOrCreateJob(any()))
          .thenReturn(Single.just(createMockResponse(List.of("result"), List.of(List.of("1")))));

      for (Functions func : functionsToTest) {
        QueryRequest request = createBasicRequest();
        QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
        selectItem.setFunction(func);
        request.setSelect(List.of(selectItem));

        clickhouseMetricService.getMetricDistribution(request).test().assertComplete();
      }
    }
  }

  private QueryRequest.OrderBy createOrderBy(String field, QueryRequest.Direction direction) {
    QueryRequest.OrderBy orderBy = new QueryRequest.OrderBy();
    orderBy.setField(field);
    orderBy.setDirection(direction);
    return orderBy;
  }
}

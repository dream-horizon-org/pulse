package org.dreamhorizon.pulseserver.service.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.TimeRange;
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

    // Default stub: return an empty valid response for any query
    GetQueryDataResponseDto<GetRawUserEventsResponseDto> emptyResponse = createMockResponse(
        List.of(), List.of());
    when(clickhouseQueryService.executeQueryOrCreateJob(any()))
        .thenReturn(Single.just(emptyResponse));
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

  @Nested
  class GetInteractionHealth {
    @Test
    void shouldBuildHealthQueryWithTopNAndDefaultOrderBy() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq.builder()
          .topN(5)
          .build();
      clickhouseMetricService.getInteractionHealth(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("SpanName as interaction_name");
      assertThat(query).contains("COUNT() as spanfreq");
      assertThat(query).contains("group by interaction_name");
      assertThat(query).contains("order by spanfreq DESC");
      assertThat(query).contains("limit 5");
      assertThat(query).contains("PulseType In ('interaction')");
    }

    @Test
    void shouldInjectInteractionNamesFilter() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq.builder()
          .interactionNames(List.of("ContestJoin", "MatchEntry"))
          .build();
      clickhouseMetricService.getInteractionHealth(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("SpanName In ('ContestJoin','MatchEntry')");
    }

    @Test
    void shouldMapTimeRangeAndFiltersCorrectly() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq.builder()
          .timeRange(TimeRange.builder().start("2026-03-01T00:00:00Z").end("2026-03-02T00:00:00Z").build())
          .filters(Map.of("platform", "Android"))
          .build();
      clickhouseMetricService.getInteractionHealth(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("Platform");
      assertThat(query).contains("Android");
    }

    @Test
    void shouldIncludeBothSuccessAndErrorCountsInSelect() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq.builder()
          .topN(10)
          .build();
      clickhouseMetricService.getInteractionHealth(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("success_count");
      assertThat(query).contains("error_count");
      assertThat(query).contains("user_excellent");
      assertThat(query).contains("user_good");
      assertThat(query).contains("user_avg");
      assertThat(query).contains("user_poor");
      assertThat(query).contains("p50");
    }

    @Test
    void shouldPropagateProjectId() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq.builder()
          .projectId("proj-456")
          .build();
      clickhouseMetricService.getInteractionHealth(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getProjectId()).isEqualTo("proj-456");
    }
  }

  @Nested
  class GetInteractionMetrics {
    @Test
    void shouldBuildMetricsQueryForApdexWithoutTimeseries() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("test").metricType("APDEX").timeseries(false).build();
      clickhouseMetricService.getInteractionMetrics(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("apdex");
      assertThat(query).doesNotContain("group by");
      assertThat(query).contains("SpanName In ('test')");
    }

    @Test
    void shouldBuildMetricsQueryForCompositeWithTimeseries() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("test").metricType("COMPOSITE").timeseries(true).build();
      clickhouseMetricService.getInteractionMetrics(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("toDateTime");
      assertThat(query).contains("as t1");
      assertThat(query).contains("group by t1");
      assertThat(query).contains("order by t1 ASC");
      assertThat(query).contains("apdex");
    }

    @Test
    void shouldBuildMetricsQueryForLatency() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("test").metricType("LATENCY").timeseries(false).build();
      clickhouseMetricService.getInteractionMetrics(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("p50");
      assertThat(query).contains("p95");
      assertThat(query).contains("p99");
    }

    @Test
    void shouldBuildMetricsQueryForErrorRate() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("test").metricType("ERROR_RATE").timeseries(false).build();
      clickhouseMetricService.getInteractionMetrics(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("success_count");
      assertThat(query).contains("error_count");
    }

    @Test
    void shouldBuildMetricsQueryForUserCategories() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("test").metricType("USER_CATEGORIES").timeseries(false).build();
      clickhouseMetricService.getInteractionMetrics(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("user_excellent");
      assertThat(query).contains("user_good");
      assertThat(query).contains("user_poor");
    }

    @Test
    void shouldThrowErrorOnInvalidMetricType() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("test").metricType("INVALID_TYPE").timeseries(false).build();
      
      clickhouseMetricService.getInteractionMetrics(req).test().assertError(IllegalArgumentException.class);
    }

    @Test
    void shouldBuildCompositeAggregateWithAllMetrics() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("test").metricType("COMPOSITE").timeseries(false).build();
      clickhouseMetricService.getInteractionMetrics(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      // Core metrics
      assertThat(query).contains("apdex");
      assertThat(query).contains("success_count");
      assertThat(query).contains("error_count");
      assertThat(query).contains("distinct_error_users");
      // Latency percentiles
      assertThat(query).contains("p50");
      assertThat(query).contains("p95");
      assertThat(query).contains("p99");
      // Stability metrics
      assertThat(query).contains("frozen_frame");
      assertThat(query).contains("unanalysed_frame");
      assertThat(query).contains("analysed_frame");
      assertThat(query).contains("crash");
      assertThat(query).contains("anr");
      // Network: all 5 codes + total count
      assertThat(query).contains("net_0");
      assertThat(query).contains("net_2xx");
      assertThat(query).contains("net_3xx");
      assertThat(query).contains("net_4xx");
      assertThat(query).contains("net_5xx");
      assertThat(query).contains("net_count");
      // User categories (raw counts)
      assertThat(query).contains("user_excellent");
      assertThat(query).contains("user_good");
      assertThat(query).contains("user_avg");
      assertThat(query).contains("user_poor");
      // Computed rates (percentages)
      assertThat(query).contains("error_rate");
      assertThat(query).contains("crash_rate");
      assertThat(query).contains("anr_rate");
      assertThat(query).contains("frozen_frame_rate");
      assertThat(query).contains("poor_user_rate");
      assertThat(query).contains("avg_user_rate");
      assertThat(query).contains("good_user_rate");
      assertThat(query).contains("excellent_user_rate");
    }

    @Test
    void shouldAutoInjectPulseTypeAndSpanNameFilters() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("ContestJoin").metricType("APDEX").timeseries(false).build();
      clickhouseMetricService.getInteractionMetrics(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("PulseType");
      assertThat(query).contains("interaction");
      assertThat(query).contains("SpanName");
      assertThat(query).contains("ContestJoin");
    }

    @Test
    void shouldTranslateUserFiltersToBackendFormat() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq.builder()
          .interactionName("test").metricType("APDEX").timeseries(false)
          .filters(Map.of("platform", "Android"))
          .build();
      clickhouseMetricService.getInteractionMetrics(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("Platform");
      assertThat(query).contains("Android");
    }
  }

  @Nested
  class GetInteractionBreakdown {
    @Test
    void shouldBuildBreakdownQueryForDeviceDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("device").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("DeviceModel as deviceModel");
      assertThat(query).contains("frozen_frame");
      assertThat(query).contains("anr");
      assertThat(query).contains("crash");
      assertThat(query).contains("group by deviceModel");
    }

    @Test
    void shouldBuildBreakdownQueryForReleaseDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("RELEASE").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("AppVersion as release");
      assertThat(query).contains("apdex");
      assertThat(query).contains("crash");
      assertThat(query).contains("anr");
      assertThat(query).contains("success_count");
      assertThat(query).contains("error_count");
      assertThat(query).contains("group by release");
    }

    @Test
    void shouldBuildBreakdownQueryForLatencyByNetworkDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("LATENCY_BY_NETWORK").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("NetworkProvider as network");
      assertThat(query).contains("p50");
      assertThat(query).contains("p95");
      assertThat(query).contains("p99");
      assertThat(query).contains("group by network");
    }

    @Test
    void shouldBuildBreakdownQueryForRegionDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("REGION").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("GeoState as region");
      assertThat(query).contains("success_count");
      assertThat(query).contains("error_count");
      assertThat(query).contains("user_poor");
      assertThat(query).contains("group by region");
    }

    @Test
    void shouldBuildBreakdownQueryForPlatformDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("PLATFORM").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("Platform as platform");
      assertThat(query).contains("error_count");
      assertThat(query).contains("user_poor");
      assertThat(query).contains("group by platform");
    }

    @Test
    void shouldBuildBreakdownQueryForOsDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("OS").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("OsVersion as os_version");
      assertThat(query).contains("error_count");
      assertThat(query).contains("user_poor");
      assertThat(query).contains("group by os_version");
    }

    @Test
    void shouldRejectUnknownDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req =
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("Custom_Field_123").build();

      clickhouseMetricService.getInteractionBreakdown(req).test()
          .assertError(IllegalArgumentException.class);
    }

    @Test
    void shouldBuildBreakdownQueryForNetworkDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("network").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("NetworkProvider as network");
      assertThat(query).contains("success_count");
      assertThat(query).contains("error_count");
      assertThat(query).contains("group by network");
    }

    @Test
    void shouldBuildBreakdownQueryForLatencyByDeviceDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("latency_by_device").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("DeviceModel as deviceModel");
      assertThat(query).contains("p50");
      assertThat(query).contains("p95");
      assertThat(query).contains("p99");
      assertThat(query).contains("group by deviceModel");
    }

    @Test
    void shouldBuildBreakdownQueryForLatencyByOsDimension() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("latency_by_os").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("OsVersion as os_version");
      assertThat(query).contains("p50");
      assertThat(query).contains("p95");
      assertThat(query).contains("p99");
      assertThat(query).contains("group by os_version");
    }

    @Test
    void shouldAutoInjectPulseTypeAndSpanNameForBreakdown() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("ContestJoin").dimension("device").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("PulseType");
      assertThat(query).contains("interaction");
      assertThat(query).contains("SpanName");
      assertThat(query).contains("ContestJoin");
    }

    @Test
    void shouldApplyDefaultLimitOf10() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq.builder()
          .interactionName("test").dimension("device").build();
      clickhouseMetricService.getInteractionBreakdown(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("limit 10");
    }
  }

  @Nested
  class GetInteractionSessions {
    @Test
    void shouldBuildSessionsQueryForSessionsScope() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("sessions").build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("Timestamp as timestamp");
      assertThat(query).contains("Duration as duration");
      assertThat(query).contains("order by timestamp DESC");
    }

    @Test
    void shouldApplyEventTypeFilter() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("sessions").eventType("crash").build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("has(Events.Name, 'device.crash')");
    }

    @Test
    void shouldBuildSessionsQueryForStatsScope() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("STATS").build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("COUNT() as total_sessions");
      assertThat(query).contains("success_count");
      assertThat(query).contains("error_count");
      assertThat(query).contains("crash");
      assertThat(query).contains("anr");
      assertThat(query).contains("apdex");
      assertThat(query).contains("p50");
      assertThat(query).contains("p95");
      assertThat(query).contains("p99");
      assertThat(query).contains("distinct_error_users");
    }

    @Test
    void shouldApplyCustomLimit() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("SESSIONS").limit(50).build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("limit 50");
    }

    @Test
    void shouldThrowErrorOnInvalidScope() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("INVALID_SCOPE").build();
      
      clickhouseMetricService.getInteractionSessions(req).test().assertError(IllegalArgumentException.class);
    }

    @Test
    void shouldApplyAnrEventTypeFilter() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("sessions").eventType("anr").build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("has(Events.Name, 'device.anr')");
    }

    @Test
    void shouldApplyFrozenFrameEventTypeFilter() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("sessions").eventType("frozen_frame").build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("has(Events.Name, 'app.jank.frozen')");
    }

    @Test
    void shouldIncludeAllSessionColumnsInSelect() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("sessions").build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("TraceId as trace_id");
      assertThat(query).contains("SpanId as span_id");
      assertThat(query).contains("StatusCode as status_code");
      assertThat(query).contains("Platform as platform");
      assertThat(query).contains("DeviceModel as device");
      assertThat(query).contains("OsVersion as os_version");
      assertThat(query).contains("AppVersion as app_version");
    }

    @Test
    void shouldAutoInjectPulseTypeAndSpanNameForSessions() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("ContestJoin").scope("sessions").build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      String query = configCaptor.getValue().getQuery();
      
      assertThat(query).contains("PulseType");
      assertThat(query).contains("interaction");
      assertThat(query).contains("SpanName");
      assertThat(query).contains("ContestJoin");
    }

    @Test
    void shouldPropagateProjectIdForSessions() {
      org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq req = 
          org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq.builder()
          .interactionName("test").scope("sessions").projectId("proj-789").build();
      clickhouseMetricService.getInteractionSessions(req).test().assertComplete();
      
      ArgumentCaptor<org.dreamhorizon.pulseserver.model.QueryConfiguration> configCaptor =
          ArgumentCaptor.forClass(org.dreamhorizon.pulseserver.model.QueryConfiguration.class);
      verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());
      assertThat(configCaptor.getValue().getProjectId()).isEqualTo("proj-789");
    }
  }
}

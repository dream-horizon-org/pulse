package in.horizonos.pulseserver.service.clickhouse;
import in.horizonos.pulseserver.client.chclient.ClickhouseQueryService;
import in.horizonos.pulseserver.dto.response.GetRawUserEventsResponseDto;
import in.horizonos.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import in.horizonos.pulseserver.model.JobCreationMode;
import in.horizonos.pulseserver.model.QueryConfiguration;
import in.horizonos.pulseserver.resources.performance.models.Functions;
import in.horizonos.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import in.horizonos.pulseserver.resources.performance.models.QueryRequest;
import in.horizonos.pulseserver.service.interaction.ClickhouseMetricService;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickhouseMetricServiceTest {

    @Mock
    private ClickhouseQueryService clickhouseQueryService;

    private ClickhouseMetricService clickhouseMetricService;

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
    class TestBasicQueryGeneration {

        @Test
        void shouldGenerateBasicQueryWithDefaultSelect() {
            QueryRequest request = createBasicRequest();

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1", "field2"),
                    List.of(List.of("value1", "value2")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            TestObserver<PerformanceMetricDistributionRes> testObserver =
                    clickhouseMetricService.getMetricDistribution(request).test();

            testObserver.assertComplete();
            testObserver.assertNoErrors();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            QueryConfiguration config = configCaptor.getValue();
            String query = config.getQuery();
            assertThat(query).contains("Select * from otel_traces");
            assertThat(query).contains("Timestamp >= toDateTime64");
            assertThat(query).contains("limit 100");
        }

        @Test
        void shouldUseCustomLimitWhenProvided() {
            QueryRequest request = createBasicRequest();
            request.setLimit(50);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("limit 50");
        }
    }

    @Nested
    class TestDataTypeMapping {

        @Test
        void shouldMapTracesDataType() {
            QueryRequest request = createBasicRequest();
            request.setDataType(QueryRequest.DataType.TRACES);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("from otel_traces");
        }

        @Test
        void shouldMapLogsDataType() {
            QueryRequest request = createBasicRequest();
            request.setDataType(QueryRequest.DataType.LOGS);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("from otel_logs");
        }

        @Test
        void shouldMapMetricsDataType() {
            QueryRequest request = createBasicRequest();
            request.setDataType(QueryRequest.DataType.METRICS);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("from otel_metrics");
        }

        @Test
        void shouldMapExceptionsDataType() {
            QueryRequest request = createBasicRequest();
            request.setDataType(QueryRequest.DataType.EXCEPTIONS);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("from stack_trace_events");
        }
    }

    @Nested
    class TestSelectClauseGeneration {

        @Test
        void shouldGenerateSelectClauseForApdex() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.APDEX);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("apdex"),
                    List.of(List.of("0.95")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as apdex");
        }

        @Test
        void shouldGenerateSelectClauseForColFunction() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.COL);
            Map<String, String> params = new HashMap<>();
            params.put("field", "span.name");
            selectItem.setParam(params);
            selectItem.setAlias("spanName");
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("spanName"),
                    List.of(List.of("testSpan")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("span.name as spanName");
        }

        @Test
        void shouldGenerateSelectClauseForCustomFunction() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.CUSTOM);
            Map<String, String> params = new HashMap<>();
            params.put("expression", "count(*)");
            selectItem.setParam(params);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("custom"),
                    List.of(List.of("100")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("count(*) as custom");
        }

        @Test
        void shouldGenerateSelectClauseForTimeBucket() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.TIME_BUCKET);
            Map<String, String> params = new HashMap<>();
            params.put("field", "Timestamp");
            params.put("bucket", "5m");
            selectItem.setParam(params);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("time_bucket"),
                    List.of(List.of("2024-01-01 00:00:00")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("Timestamp");
            assertThat(query).contains("as time_bucket");
        }

        @Test
        void shouldGenerateSelectClauseForMultipleSelectItems() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem1 = new QueryRequest.SelectItem();
            selectItem1.setFunction(Functions.APDEX);
            selectItem1.setAlias("apdexScore");

            QueryRequest.SelectItem selectItem2 = new QueryRequest.SelectItem();
            selectItem2.setFunction(Functions.DURATION_P99);
            selectItem2.setAlias("p99Duration");

            request.setSelect(List.of(selectItem1, selectItem2));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("apdexScore", "p99Duration"),
                    List.of(List.of("0.95", "500")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as apdexScore");
            assertThat(query).contains("as p99Duration");
        }

        @Test
        void shouldUseDefaultDisplayNameWhenAliasNotProvided() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.CRASH);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("crash"),
                    List.of(List.of("0")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as crash");
        }
    }

    @Nested
    class TestFilterGeneration {

        @Test
        void shouldGenerateLikeFilter() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("span.name");
            filter.setOperator(QueryRequest.Operator.LIKE);
            filter.setValue(List.of("test%"));
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("span.name like 'test%'");
        }

        @Test
        void shouldGenerateInFilter() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("appVersion");
            filter.setOperator(QueryRequest.Operator.IN);
            filter.setValue(List.of("1.0.0", "1.1.0", "1.2.0"));
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("appVersion In ('1.0.0','1.1.0','1.2.0')");
        }

        @Test
        void shouldGenerateEqFilter() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("status");
            filter.setOperator(QueryRequest.Operator.EQ);
            filter.setValue(List.of("success"));
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("status = 'success'");
        }

        @Test
        void shouldGenerateAdditionalFilter() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setOperator(QueryRequest.Operator.ADDITIONAL);
            filter.setValue(List.of("customCondition = true"));
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("And (customCondition = true)");
        }

        @Test
        void shouldGenerateMultipleFilters() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter1 = new QueryRequest.Filter();
            filter1.setField("span.name");
            filter1.setOperator(QueryRequest.Operator.LIKE);
            filter1.setValue(List.of("test%"));

            QueryRequest.Filter filter2 = new QueryRequest.Filter();
            filter2.setField("status");
            filter2.setOperator(QueryRequest.Operator.EQ);
            filter2.setValue(List.of("success"));

            request.setFilters(List.of(filter1, filter2));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("span.name like 'test%'");
            assertThat(query).contains("status = 'success'");
        }

        @Test
        void shouldFormatNumericFilterValuesWithoutQuotes() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("duration");
            filter.setOperator(QueryRequest.Operator.IN);
            filter.setValue(List.of(100, 200, 300));
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("duration In (100,200,300)");
        }
    }

    @Nested
    class TestGroupByClause {

        @Test
        void shouldGenerateGroupByClause() {
            QueryRequest request = createBasicRequest();
            request.setGroupBy(List.of("span.name", "status"));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("span.name", "status"),
                    List.of(List.of("testSpan", "success")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("group by span.name,status");
        }

        @Test
        void shouldNotIncludeGroupByWhenEmpty() {
            QueryRequest request = createBasicRequest();
            request.setGroupBy(new ArrayList<>());

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).doesNotContain("group by");
        }
    }

    @Nested
    class TestOrderByClause {

        @Test
        void shouldGenerateOrderByClause() {
            QueryRequest request = createBasicRequest();
            QueryRequest.OrderBy orderBy = new QueryRequest.OrderBy();
            orderBy.setField("duration");
            orderBy.setDirection(QueryRequest.Direction.DESC);
            request.setOrderBy(List.of(orderBy));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("duration"),
                    List.of(List.of("500")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("order by duration DESC");
        }

        @Test
        void shouldGenerateMultipleOrderByClauses() {
            QueryRequest request = createBasicRequest();
            QueryRequest.OrderBy orderBy1 = new QueryRequest.OrderBy();
            orderBy1.setField("duration");
            orderBy1.setDirection(QueryRequest.Direction.DESC);

            QueryRequest.OrderBy orderBy2 = new QueryRequest.OrderBy();
            orderBy2.setField("span.name");
            orderBy2.setDirection(QueryRequest.Direction.ASC);

            request.setOrderBy(List.of(orderBy1, orderBy2));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("duration", "span.name"),
                    List.of(List.of("500", "testSpan")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("order by duration DESC, span.name ASC");
        }
    }

    @Nested
    class TestResponseMapping {

        @Test
        void shouldMapResponseCorrectly() {
            QueryRequest request = createBasicRequest();

            List<String> fieldNames = List.of("field1", "field2", "field3");
            List<List<Object>> rowData = List.of(
                    List.of("value1", "value2", "value3"),
                    List.of("value4", "value5", "value6"));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse =
                    createMockResponse(fieldNames, rowData);

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            TestObserver<PerformanceMetricDistributionRes> testObserver =
                    clickhouseMetricService.getMetricDistribution(request).test();

            testObserver.assertComplete();
            testObserver.assertNoErrors();

            PerformanceMetricDistributionRes result = testObserver.values().get(0);
            assertThat(result.getFields()).isEqualTo(fieldNames);
            assertThat(result.getRows()).hasSize(2);
            assertThat(result.getRows().get(0)).containsExactly("value1", "value2", "value3");
            assertThat(result.getRows().get(1)).containsExactly("value4", "value5", "value6");
        }

        @Test
        void shouldHandleNullValuesInResponse() {
            QueryRequest request = createBasicRequest();

            List<String> fieldNames = List.of("field1", "field2");
            List<List<Object>> rowData = new ArrayList<>();
            List<Object> row1 = new ArrayList<>();
            row1.add("value1");
            row1.add(null);
            List<Object> row2 = new ArrayList<>();
            row2.add(null);
            row2.add("value2");
            rowData.add(row1);
            rowData.add(row2);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse =
                    createMockResponse(fieldNames, rowData);

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            TestObserver<PerformanceMetricDistributionRes> testObserver =
                    clickhouseMetricService.getMetricDistribution(request).test();

            testObserver.assertComplete();
            testObserver.assertNoErrors();

            PerformanceMetricDistributionRes result = testObserver.values().get(0);
            assertThat(result.getRows().get(0)).containsExactly("value1", "");
            assertThat(result.getRows().get(1)).containsExactly("", "value2");
        }

        @Test
        void shouldHandleEmptyResponse() {
            QueryRequest request = createBasicRequest();

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse =
                    createMockResponse(List.of(), List.of());

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            TestObserver<PerformanceMetricDistributionRes> testObserver =
                    clickhouseMetricService.getMetricDistribution(request).test();

            testObserver.assertComplete();
            testObserver.assertNoErrors();

            PerformanceMetricDistributionRes result = testObserver.values().get(0);
            assertThat(result.getFields()).isEmpty();
            assertThat(result.getRows()).isEmpty();
        }
    }

    @Nested
    class TestQueryConfiguration {

        @Test
        void shouldSetCorrectQueryConfiguration() {
            QueryRequest request = createBasicRequest();

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            QueryConfiguration config = configCaptor.getValue();
            assertThat(config.getTimeoutMs()).isEqualTo(2000);
            assertThat(config.getJobCreationMode()).isEqualTo(JobCreationMode.JOB_CREATION_OPTIONAL);
        }
    }

    @Nested
    class TestComplexQueries {

        @Test
        void shouldGenerateComplexQueryWithAllClauses() {
            QueryRequest request = createBasicRequest();

            // Select
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.APDEX);
            selectItem.setAlias("apdexScore");
            request.setSelect(List.of(selectItem));

            // Filters
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("span.name");
            filter.setOperator(QueryRequest.Operator.LIKE);
            filter.setValue(List.of("test%"));
            request.setFilters(List.of(filter));

            // Group by
            request.setGroupBy(List.of("span.name"));

            // Order by
            QueryRequest.OrderBy orderBy = new QueryRequest.OrderBy();
            orderBy.setField("apdexScore");
            orderBy.setDirection(QueryRequest.Direction.DESC);
            request.setOrderBy(List.of(orderBy));

            // Limit
            request.setLimit(25);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("apdexScore"),
                    List.of(List.of("0.95")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as apdexScore");
            assertThat(query).contains("span.name like 'test%'");
            assertThat(query).contains("group by span.name");
            assertThat(query).contains("order by apdexScore DESC");
            assertThat(query).contains("limit 25");
        }
    }

    @Nested
    class TestErrorHandling {

        @Test
        void shouldPropagateErrorFromClickhouseQueryService() {
            QueryRequest request = createBasicRequest();

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.error(new RuntimeException("Database connection failed")));

            TestObserver<PerformanceMetricDistributionRes> testObserver =
                    clickhouseMetricService.getMetricDistribution(request).test();

            testObserver.assertError(RuntimeException.class)
                    .assertError(throwable -> throwable.getMessage().equals("Database connection failed"));
        }
    }

    @Nested
    class TestTimeRangeFormatting {

        @Test
        void shouldFormatTimeRangeCorrectly() {
            QueryRequest request = createBasicRequest();
            QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
            timeRange.setStart("2024-01-15T10:30:00Z");
            timeRange.setEnd("2024-01-15T11:45:00Z");
            request.setTimeRange(timeRange);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("Timestamp >= toDateTime64('2024-01-15 10:30:00',9,'UTC')");
            assertThat(query).contains("Timestamp <= toDateTime64('2024-01-15 11:45:00',9,'UTC')");
        }
    }

    @Nested
    class TestAllFunctionTypes {

        @Test
        void shouldGenerateSelectClauseForCrash() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.CRASH);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("crash"),
                    List.of(List.of("0")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as crash");
        }

        @Test
        void shouldGenerateSelectClauseForAnr() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.ANR);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("anr"),
                    List.of(List.of("0")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as anr");
        }

        @Test
        void shouldGenerateSelectClauseForFrozenFrame() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.FROZEN_FRAME);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("frozen_frame"),
                    List.of(List.of("0")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as frozen_frame");
        }

        @Test
        void shouldGenerateSelectClauseForAnalysedFrame() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.ANALYSED_FRAME);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("analysed_frame"),
                    List.of(List.of("100")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as analysed_frame");
        }

        @Test
        void shouldGenerateSelectClauseForUnanalysedFrame() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.UNANALYSED_FRAME);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("unanalysed_frame"),
                    List.of(List.of("50")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as unanalysed_frame");
        }

        @Test
        void shouldGenerateSelectClauseForDurationP99() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.DURATION_P99);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("duration_p99"),
                    List.of(List.of("500")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as duration_p99");
        }

        @Test
        void shouldGenerateSelectClauseForDurationP50() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.DURATION_P50);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("duration_p50"),
                    List.of(List.of("200")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as duration_p50");
        }

        @Test
        void shouldGenerateSelectClauseForDurationP95() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.DURATION_P95);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("duration_p95"),
                    List.of(List.of("400")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as duration_p95");
        }

        @Test
        void shouldGenerateSelectClauseForInteractionSuccessCount() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.INTERACTION_SUCCESS_COUNT);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("successInteractionCount"),
                    List.of(List.of("1000")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as successInteractionCount");
        }

        @Test
        void shouldGenerateSelectClauseForInteractionErrorCount() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.INTERACTION_ERROR_COUNT);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("errorInteractionCount"),
                    List.of(List.of("50")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as errorInteractionCount");
        }

        @Test
        void shouldGenerateSelectClauseForInteractionErrorDistinctUsers() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.INTERACTION_ERROR_DISTINCT_USERS);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("distinctUsers"),
                    List.of(List.of("25")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as distinctUsers");
        }

        @Test
        void shouldGenerateSelectClauseForUserCategoryAverage() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.USER_CATEGORY_AVERAGE);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("midUptimeUser2"),
                    List.of(List.of("30")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as midUptimeUser2");
        }

        @Test
        void shouldGenerateSelectClauseForUserCategoryGood() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.USER_CATEGORY_GOOD);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("midUptimeUser1"),
                    List.of(List.of("40")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as midUptimeUser1");
        }

        @Test
        void shouldGenerateSelectClauseForUserCategoryPoor() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.USER_CATEGORY_POOR);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("highUptimeUser"),
                    List.of(List.of("20")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as highUptimeUser");
        }

        @Test
        void shouldGenerateSelectClauseForUserCategoryExcellent() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.USER_CATEGORY_EXCELLENT);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("lowUptimeUser"),
                    List.of(List.of("10")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as lowUptimeUser");
        }

        @Test
        void shouldGenerateSelectClauseForNet0() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.NET_0);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("connectionerror"),
                    List.of(List.of("5")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as connectionerror");
        }

        @Test
        void shouldGenerateSelectClauseForNet2XX() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.NET_2XX);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("net2XX"),
                    List.of(List.of("100")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as net2XX");
        }

        @Test
        void shouldGenerateSelectClauseForNet3XX() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.NET_3XX);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("net3XX"),
                    List.of(List.of("10")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as net3XX");
        }

        @Test
        void shouldGenerateSelectClauseForNet4XX() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.NET_4XX);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("net4XX"),
                    List.of(List.of("15")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as net4XX");
        }

        @Test
        void shouldGenerateSelectClauseForNet5XX() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.NET_5XX);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("net5XX"),
                    List.of(List.of("3")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as net5XX");
        }

        @Test
        void shouldGenerateSelectClauseForArrToStr() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.ARR_TO_STR);
            Map<String, String> params = new HashMap<>();
            params.put("field", "tags");
            selectItem.setParam(params);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("arrToString"),
                    List.of(List.of("tag1,tag2")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("tags");
            assertThat(query).contains("as arrToString");
        }
    }

    @Nested
    class TestFormatMethodEdgeCases {

        @Test
        void shouldFormatEmptyFilterList() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("test");
            filter.setOperator(QueryRequest.Operator.IN);
            filter.setValue(new ArrayList<>());
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("test In ()");
        }

        @Test
        void shouldFormatMixedTypeFilterValues() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("status");
            filter.setOperator(QueryRequest.Operator.IN);
            List<Object> mixedValues = new ArrayList<>();
            mixedValues.add("success");
            mixedValues.add(200);
            mixedValues.add(true);
            filter.setValue(mixedValues);
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("'success'");
            assertThat(query).contains("200");
            assertThat(query).contains("true");
        }

        @Test
        void shouldFormatStringWithSpecialCharacters() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("message");
            filter.setOperator(QueryRequest.Operator.LIKE);
            filter.setValue(List.of("test%value_with_underscores"));
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("'test%value_with_underscores'");
        }
    }

    @Nested
    class TestFormatGroupByEdgeCases {

        @Test
        void shouldFormatSingleGroupByField() {
            QueryRequest request = createBasicRequest();
            request.setGroupBy(List.of("span.name"));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("span.name"),
                    List.of(List.of("testSpan")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("group by span.name");
        }

        @Test
        void shouldFormatGroupByWithSpecialCharacters() {
            QueryRequest request = createBasicRequest();
            request.setGroupBy(List.of("field_name", "field.name", "field_name_2"));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field_name"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("group by field_name,field.name,field_name_2");
        }
    }

    @Nested
    class TestFilterEdgeCases {

        @Test
        void shouldHandleSingleValueInInFilter() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("status");
            filter.setOperator(QueryRequest.Operator.IN);
            filter.setValue(List.of("active"));
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("status In ('active')");
        }

        @Test
        void shouldHandleEqFilterWithNumericValue() {
            QueryRequest request = createBasicRequest();
            QueryRequest.Filter filter = new QueryRequest.Filter();
            filter.setField("count");
            filter.setOperator(QueryRequest.Operator.EQ);
            List<Object> value = new ArrayList<>();
            value.add(100);
            filter.setValue(value);
            request.setFilters(List.of(filter));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("count = 100");
        }
    }

    @Nested
    class TestSelectItemEdgeCases {

        @Test
        void shouldHandleNullAliasForAllFunctions() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.DURATION_P99);
            selectItem.setAlias(null);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("duration_p99"),
                    List.of(List.of("500")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("as duration_p99");
        }

        @Test
        void shouldHandleTimeBucketWithDifferentBucketSizes() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.TIME_BUCKET);
            Map<String, String> params = new HashMap<>();
            params.put("field", "Timestamp");
            params.put("bucket", "1h");
            selectItem.setParam(params);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("time_bucket"),
                    List.of(List.of("2024-01-01 00:00:00")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("Timestamp");
            assertThat(query).contains("as time_bucket");
        }

        @Test
        void shouldHandleTimeBucketWithDayBucket() {
            QueryRequest request = createBasicRequest();
            QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
            selectItem.setFunction(Functions.TIME_BUCKET);
            Map<String, String> params = new HashMap<>();
            params.put("field", "Timestamp");
            params.put("bucket", "1d");
            selectItem.setParam(params);
            request.setSelect(List.of(selectItem));

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("time_bucket"),
                    List.of(List.of("2024-01-01 00:00:00")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("Timestamp");
        }
    }

    @Nested
    class TestResponseMappingEdgeCases {

        @Test
        void shouldHandleNonStringObjectTypesInResponse() {
            QueryRequest request = createBasicRequest();

            List<String> fieldNames = List.of("field1", "field2", "field3");
            List<List<Object>> rowData = new ArrayList<>();
            List<Object> row1 = new ArrayList<>();
            row1.add("stringValue");
            row1.add(123);
            row1.add(true);
            rowData.add(row1);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse =
                    createMockResponse(fieldNames, rowData);

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            TestObserver<PerformanceMetricDistributionRes> testObserver =
                    clickhouseMetricService.getMetricDistribution(request).test();

            testObserver.assertComplete();
            testObserver.assertNoErrors();

            PerformanceMetricDistributionRes result = testObserver.values().get(0);
            assertThat(result.getRows().get(0)).containsExactly("stringValue", "123", "true");
        }

        @Test
        void shouldHandleRowsWithDifferentLengths() {
            QueryRequest request = createBasicRequest();

            List<String> fieldNames = List.of("field1", "field2", "field3");
            List<List<Object>> rowData = new ArrayList<>();
            List<Object> row1 = new ArrayList<>();
            row1.add("value1");
            row1.add("value2");
            row1.add("value3");
            List<Object> row2 = new ArrayList<>();
            row2.add("value4");
            row2.add("value5");
            rowData.add(row1);
            rowData.add(row2);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse =
                    createMockResponse(fieldNames, rowData);

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            TestObserver<PerformanceMetricDistributionRes> testObserver =
                    clickhouseMetricService.getMetricDistribution(request).test();

            testObserver.assertComplete();
            testObserver.assertNoErrors();

            PerformanceMetricDistributionRes result = testObserver.values().get(0);
            assertThat(result.getRows()).hasSize(2);
            assertThat(result.getRows().get(0)).hasSize(3);
            assertThat(result.getRows().get(1)).hasSize(2);
        }

        @Test
        void shouldHandleLargeResponse() {
            QueryRequest request = createBasicRequest();

            List<String> fieldNames = List.of("field1", "field2");
            List<List<Object>> rowData = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                List<Object> row = new ArrayList<>();
                row.add("value" + i);
                row.add("value" + (i + 100));
                rowData.add(row);
            }

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse =
                    createMockResponse(fieldNames, rowData);

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            TestObserver<PerformanceMetricDistributionRes> testObserver =
                    clickhouseMetricService.getMetricDistribution(request).test();

            testObserver.assertComplete();
            testObserver.assertNoErrors();

            PerformanceMetricDistributionRes result = testObserver.values().get(0);
            assertThat(result.getRows()).hasSize(100);
            assertThat(result.getFields()).hasSize(2);
        }
    }

    @Nested
    class TestLimitEdgeCases {

        @Test
        void shouldHandleZeroLimit() {
            QueryRequest request = createBasicRequest();
            request.setLimit(0);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("limit 0");
        }

        @Test
        void shouldHandleLargeLimit() {
            QueryRequest request = createBasicRequest();
            request.setLimit(10000);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("limit 10000");
        }
    }

    @Nested
    class TestEmptyAndNullCollections {

        @Test
        void shouldHandleNullSelectList() {
            QueryRequest request = createBasicRequest();
            request.setSelect(null);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("Select *");
        }

        @Test
        void shouldHandleNullFilters() {
            QueryRequest request = createBasicRequest();
            request.setFilters(null);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).doesNotContain("And");
            assertThat(query).contains("Timestamp >=");
        }

        @Test
        void shouldHandleNullGroupBy() {
            QueryRequest request = createBasicRequest();
            request.setGroupBy(null);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).doesNotContain("group by");
        }

        @Test
        void shouldHandleNullOrderBy() {
            QueryRequest request = createBasicRequest();
            request.setOrderBy(null);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).doesNotContain("order by");
        }

        @Test
        void shouldHandleNullLimit() {
            QueryRequest request = createBasicRequest();
            request.setLimit(null);

            GetQueryDataResponseDto<GetRawUserEventsResponseDto> mockResponse = createMockResponse(
                    List.of("field1"),
                    List.of(List.of("value1")));

            when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
                    .thenReturn(Single.just(mockResponse));

            clickhouseMetricService.getMetricDistribution(request).test().assertComplete();

            ArgumentCaptor<QueryConfiguration> configCaptor =
                    ArgumentCaptor.forClass(QueryConfiguration.class);
            verify(clickhouseQueryService).executeQueryOrCreateJob(configCaptor.capture());

            String query = configCaptor.getValue().getQuery();
            assertThat(query).contains("limit 100");
        }
    }
}

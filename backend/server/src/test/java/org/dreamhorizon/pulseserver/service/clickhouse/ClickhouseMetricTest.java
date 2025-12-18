package org.dreamhorizon.pulseserver.service.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.JobCreationMode;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.dreamhorizon.pulseserver.service.interaction.ClickhouseMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

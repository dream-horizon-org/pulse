package org.dreamhorizon.pulseserver.dao.webvitals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalByScreenRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalSummaryRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalTrendRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebVitalsDaoTest {

  @Mock ClickhouseQueryService clickhouseQueryService;

  private WebVitalsDao webVitalsDao;

  @BeforeEach
  void setUp() {
    webVitalsDao = new WebVitalsDao(clickhouseQueryService);
  }

  @Nested
  class GetSummary {

    @Test
    void should_substitute_all_query_params_in_global_summary_query() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalSummaryRow> mockResponse =
            QueryResultResponse.<WebVitalSummaryRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalSummaryRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getSummary(startTime, endTime, null).blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalSummaryRow.class));

        String query = captor.getValue().getQuery();
        assertThat(query).doesNotContain("${").doesNotContain("}");
        assertThat(query).contains("AND Platform = 'web'");
        assertThat(query).contains("AND PulseType = 'web_vital'");
        assertThat(query).contains("quantile(0.75)(toFloat64(Attributes['web_vital.value']))");
      }
    }

    @Test
    void should_substitute_screen_name_in_per_screen_summary_query() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalSummaryRow> mockResponse =
            QueryResultResponse.<WebVitalSummaryRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalSummaryRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getSummary(startTime, endTime, "HomeScreen").blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalSummaryRow.class));

        String query = captor.getValue().getQuery();
        assertThat(query).doesNotContain("${").doesNotContain("}");
        assertThat(query).contains("AND ScreenName = 'HomeScreen'");
        assertThat(query).contains("AND Platform = 'web'");
        assertThat(query).contains("AND PulseType = 'web_vital'");
      }
    }

    @Test
    void should_route_to_global_query_when_screenName_null() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalSummaryRow> mockResponse =
            QueryResultResponse.<WebVitalSummaryRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalSummaryRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getSummary(startTime, endTime, null).blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalSummaryRow.class));

        String query = captor.getValue().getQuery();
        assertThat(query).doesNotContain("AND ScreenName =");
      }
    }

    @Test
    void should_set_useQueryConditionCache_true_on_summary() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalSummaryRow> mockResponse =
            QueryResultResponse.<WebVitalSummaryRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalSummaryRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getSummary(startTime, endTime, null).blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalSummaryRow.class));

        assertThat(captor.getValue().isUseQueryConditionCache()).isTrue();
      }
    }
  }

  @Nested
  class GetTrend {

    @Test
    void should_substitute_all_query_params_in_global_trend_query() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalTrendRow> mockResponse =
            QueryResultResponse.<WebVitalTrendRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalTrendRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getTrend(startTime, endTime, "LCP", 30, null).blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalTrendRow.class));

        String query = captor.getValue().getQuery();
        assertThat(query).doesNotContain("${").doesNotContain("}");
        assertThat(query).contains("AND Platform = 'web'");
        assertThat(query).contains("AND PulseType = 'web_vital'");
        assertThat(query).contains("AND Attributes['web_vital.name'] = 'LCP'");
      }
    }

    @Test
    void should_substitute_screen_name_in_per_screen_trend_query() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalTrendRow> mockResponse =
            QueryResultResponse.<WebVitalTrendRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalTrendRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getTrend(startTime, endTime, "INP", 30, "HomeScreen").blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalTrendRow.class));

        String query = captor.getValue().getQuery();
        assertThat(query).doesNotContain("${").doesNotContain("}");
        assertThat(query).contains("AND ScreenName = 'HomeScreen'");
      }
    }

    @Test
    void should_set_useQueryConditionCache_true_on_trend() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalTrendRow> mockResponse =
            QueryResultResponse.<WebVitalTrendRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalTrendRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getTrend(startTime, endTime, "CLS", 30, null).blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalTrendRow.class));

        assertThat(captor.getValue().isUseQueryConditionCache()).isTrue();
      }
    }
  }

  @Nested
  class GetByScreen {

    @Test
    void should_substitute_all_query_params_in_by_screen_query() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalByScreenRow> mockResponse =
            QueryResultResponse.<WebVitalByScreenRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalByScreenRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getByScreen(startTime, endTime, "FCP").blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalByScreenRow.class));

        String query = captor.getValue().getQuery();
        assertThat(query).doesNotContain("${").doesNotContain("}");
        assertThat(query).contains("AND Platform = 'web'");
        assertThat(query).contains("AND PulseType = 'web_vital'");
        assertThat(query).contains("AND Attributes['web_vital.name'] = 'FCP'");
      }
    }

    @Test
    void should_set_useQueryConditionCache_true_on_by_screen() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalByScreenRow> mockResponse =
            QueryResultResponse.<WebVitalByScreenRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalByScreenRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getByScreen(startTime, endTime, "TTFB").blockingGet();

        ArgumentCaptor<QueryConfiguration> captor = ArgumentCaptor.forClass(QueryConfiguration.class);
        verify(clickhouseQueryService, times(1))
            .executeQueryOrCreateJob(captor.capture(), eq(WebVitalByScreenRow.class));

        assertThat(captor.getValue().isUseQueryConditionCache()).isTrue();
      }
    }
  }

  @Nested
  class ProjectContextCalls {

    @Test
    void should_call_ProjectContext_requireProjectId_on_get_summary() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalSummaryRow> mockResponse =
            QueryResultResponse.<WebVitalSummaryRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalSummaryRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getSummary(startTime, endTime, null).blockingGet();

        ctx.verify(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId);
      }
    }

    @Test
    void should_call_ProjectContext_requireProjectId_on_get_trend() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalTrendRow> mockResponse =
            QueryResultResponse.<WebVitalTrendRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalTrendRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getTrend(startTime, endTime, "LCP", 30, null).blockingGet();

        ctx.verify(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId);
      }
    }

    @Test
    void should_call_ProjectContext_requireProjectId_on_get_by_screen() {
      try (MockedStatic<org.dreamhorizon.pulseserver.context.ProjectContext> ctx =
          org.mockito.Mockito.mockStatic(org.dreamhorizon.pulseserver.context.ProjectContext.class)) {
        ctx.when(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId)
            .thenReturn("test-project");

        QueryResultResponse<WebVitalByScreenRow> mockResponse =
            QueryResultResponse.<WebVitalByScreenRow>builder().rows(List.of()).build();
        when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class), eq(WebVitalByScreenRow.class)))
            .thenReturn(Single.just(mockResponse));

        Instant startTime = Instant.parse("2026-05-10T00:00:00Z");
        Instant endTime = Instant.parse("2026-05-10T01:00:00Z");

        webVitalsDao.getByScreen(startTime, endTime, "FCP").blockingGet();

        ctx.verify(org.dreamhorizon.pulseserver.context.ProjectContext::requireProjectId);
      }
    }
  }
}

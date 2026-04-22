package org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelConversionSummaryRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelResultRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FunnelResultsDaoTest {

  private static final String PROJECT = "test-project";

  @Mock
  ClickhouseQueryService clickhouseQueryService;

  FunnelResultsDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(PROJECT);
    dao = new FunnelResultsDao(clickhouseQueryService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  class QueryLatest {
    @Test
    void shouldReturnRows() {
      FunnelResultRow r1 = FunnelResultRow.builder()
          .stepIndex(0).stepName("Step1").userCount(100L)
          .conversionPct(100.0).medianStepSeconds(0L).build();
      QueryResultResponse<FunnelResultRow> resp =
          QueryResultResponse.<FunnelResultRow>builder()
              .rows(Arrays.asList(r1)).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(FunnelResultRow.class)))
          .thenReturn(Single.just(resp));

      List<FunnelResultRow> result = dao.queryLatest(PROJECT, 1L).blockingGet();
      assertEquals(1, result.size());
      assertEquals("Step1", result.get(0).getStepName());
    }

    @Test
    void shouldReturnEmptyOnNullRows() {
      QueryResultResponse<FunnelResultRow> resp =
          QueryResultResponse.<FunnelResultRow>builder().rows(null).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(FunnelResultRow.class)))
          .thenReturn(Single.just(resp));

      assertTrue(dao.queryLatest(PROJECT, 1L).blockingGet().isEmpty());
    }
  }

  @Nested
  class QueryConversionSummaries {
    @Test
    void shouldReturnEmptyMapForNullIds() {
      Map<Long, FunnelConversionSummaryRow> result =
          dao.queryConversionSummaries(PROJECT, null).blockingGet();
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyMapForEmptyIds() {
      Map<Long, FunnelConversionSummaryRow> result =
          dao.queryConversionSummaries(PROJECT, Collections.emptyList()).blockingGet();
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnMappedSummaries() {
      FunnelConversionSummaryRow row = FunnelConversionSummaryRow.builder()
          .funnelId(1L).conversionPct(75.0).conversionTrend(5.0).build();
      FunnelConversionSummaryRow nullIdRow = FunnelConversionSummaryRow.builder()
          .funnelId(null).conversionPct(99.0).build();
      QueryResultResponse<FunnelConversionSummaryRow> resp =
          QueryResultResponse.<FunnelConversionSummaryRow>builder()
              .rows(Arrays.asList(row, nullIdRow)).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(FunnelConversionSummaryRow.class)))
          .thenReturn(Single.just(resp));

      Map<Long, FunnelConversionSummaryRow> result =
          dao.queryConversionSummaries(PROJECT, Arrays.asList(1L, 2L)).blockingGet();

      assertEquals(1, result.size());
      assertEquals(75.0, result.get(1L).getConversionPct());
    }

    @Test
    void shouldHandleNullRows() {
      QueryResultResponse<FunnelConversionSummaryRow> resp =
          QueryResultResponse.<FunnelConversionSummaryRow>builder().rows(null).build();
      when(clickhouseQueryService.executeQueryOrCreateJob(
          any(QueryConfiguration.class), eq(FunnelConversionSummaryRow.class)))
          .thenReturn(Single.just(resp));

      Map<Long, FunnelConversionSummaryRow> result =
          dao.queryConversionSummaries(PROJECT, Collections.singletonList(1L)).blockingGet();
      assertTrue(result.isEmpty());
    }
  }
}

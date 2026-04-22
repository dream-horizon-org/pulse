package org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.models.JourneyResultRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JourneyResultsDaoTest {

  private static final String PROJECT = "test-project";

  @Mock
  ClickhouseQueryService clickhouseQueryService;

  JourneyResultsDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(PROJECT);
    dao = new JourneyResultsDao(clickhouseQueryService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldReturnRows() {
    JourneyResultRow row = JourneyResultRow.builder()
        .direction("forward").posFrom(0).eventFrom("a")
        .posTo(1).eventTo("b").userCount(10L).build();
    QueryResultResponse<JourneyResultRow> resp =
        QueryResultResponse.<JourneyResultRow>builder()
            .rows(Collections.singletonList(row)).build();
    when(clickhouseQueryService.executeQueryOrCreateJob(
        any(QueryConfiguration.class), eq(JourneyResultRow.class)))
        .thenReturn(Single.just(resp));

    List<JourneyResultRow> result = dao.queryLatest(PROJECT, 1L, "forward").blockingGet();
    assertEquals(1, result.size());
    assertEquals("forward", result.get(0).getDirection());
  }

  @Test
  void shouldReturnEmptyOnNullResponse() {
    when(clickhouseQueryService.executeQueryOrCreateJob(
        any(QueryConfiguration.class), eq(JourneyResultRow.class)))
        .thenReturn(Single.just(null));

    List<JourneyResultRow> result = dao.queryLatest(PROJECT, 1L, "forward").blockingGet();
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void shouldReturnEmptyOnNullRows() {
    QueryResultResponse<JourneyResultRow> resp =
        QueryResultResponse.<JourneyResultRow>builder().rows(null).build();
    when(clickhouseQueryService.executeQueryOrCreateJob(
        any(QueryConfiguration.class), eq(JourneyResultRow.class)))
        .thenReturn(Single.just(resp));

    assertTrue(dao.queryLatest(PROJECT, 1L, "backward").blockingGet().isEmpty());
  }
}

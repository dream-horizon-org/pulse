package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffCauseRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffEvidenceRow;
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
class FunnelDropoffDaoTest {

  private static final String PROJECT = "test-project";

  @Mock ClickhouseQueryService clickhouseQueryService;

  FunnelDropoffDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(PROJECT);
    dao = new FunnelDropoffDao(clickhouseQueryService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  class QueryCauses {
    @Test
    void shouldReturnMappedRowsFromPrecomputedAttribution() {
      // First call (precomputed) returns a row → no fallback needed.
      FunnelDropoffCauseRow row = FunnelDropoffCauseRow.builder()
          .causeKind("crash").causeKey("NPE@Checkout").causeLabel("NPE @ Checkout")
          .dropoffCohort(100L).dropoffAffected(42L)
          .converterCohort(80L).converterAffected(2L)
          .lift(5.6).exampleSessions("s-1,s-2,s-3").build();
      QueryResultResponse<FunnelDropoffCauseRow> resp =
          QueryResultResponse.<FunnelDropoffCauseRow>builder()
              .rows(List.of(row)).build();
      when(clickhouseQueryService.executeGenericQueryWithGlobalPool(
          any(QueryConfiguration.class), eq(FunnelDropoffCauseRow.class)))
          .thenReturn(Single.just(resp));

      List<FunnelDropoffCauseRow> result =
          dao.queryCauses(PROJECT, 1L, 0, "2026-04-23 10:00:00", "SESSIONS").blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getCauseKind()).isEqualTo("crash");
      assertThat(result.get(0).getLift()).isEqualTo(5.6);
    }

    @Test
    void shouldReturnEmptyWhenAttributionTableHasNoRows() {
      QueryResultResponse<FunnelDropoffCauseRow> empty =
          QueryResultResponse.<FunnelDropoffCauseRow>builder()
              .rows(Collections.emptyList()).build();
      when(clickhouseQueryService.executeGenericQueryWithGlobalPool(
          any(QueryConfiguration.class), eq(FunnelDropoffCauseRow.class)))
          .thenReturn(Single.just(empty));

      List<FunnelDropoffCauseRow> result =
          dao.queryCauses(PROJECT, 1L, 0, null, "SESSIONS").blockingGet();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenRowsNull() {
      QueryResultResponse<FunnelDropoffCauseRow> resp =
          QueryResultResponse.<FunnelDropoffCauseRow>builder().rows(null).build();
      when(clickhouseQueryService.executeGenericQueryWithGlobalPool(
          any(QueryConfiguration.class), eq(FunnelDropoffCauseRow.class)))
          .thenReturn(Single.just(resp));

      List<FunnelDropoffCauseRow> result =
          dao.queryCauses(PROJECT, 1L, 0, null, "SESSIONS").blockingGet();
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class QueryEvidence {
    @Test
    void shouldShortCircuitForEmptySessionIds() {
      List<FunnelDropoffEvidenceRow> result =
          dao.queryEvidence(PROJECT, 1L, 0, null, "SESSIONS", Collections.emptyList())
              .blockingGet();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldShortCircuitForNullSessionIds() {
      List<FunnelDropoffEvidenceRow> result =
          dao.queryEvidence(PROJECT, 1L, 0, null, "SESSIONS", null).blockingGet();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnMappedEvidenceRows() {
      FunnelDropoffEvidenceRow row = FunnelDropoffEvidenceRow.builder()
          .sessionId("s-1").userId("u-1").lastReachedAt("2026-04-23 10:00:00")
          .traceId("t-1").screen("Checkout").appVersion("1.2.3").platform("android")
          .build();
      QueryResultResponse<FunnelDropoffEvidenceRow> resp =
          QueryResultResponse.<FunnelDropoffEvidenceRow>builder()
              .rows(List.of(row)).build();
      when(clickhouseQueryService.executeGenericQueryWithGlobalPool(
          any(QueryConfiguration.class), eq(FunnelDropoffEvidenceRow.class)))
          .thenReturn(Single.just(resp));

      List<FunnelDropoffEvidenceRow> result =
          dao.queryEvidence(PROJECT, 1L, 0, "2026-04-23 10:00:00", "SESSIONS",
                  List.of("s-1")).blockingGet();
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getSessionId()).isEqualTo("s-1");
      assertThat(result.get(0).getTraceId()).isEqualTo("t-1");
    }
  }
}

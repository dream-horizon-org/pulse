package org.dreamhorizon.pulseserver.service.productAnalysis.funnel.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.FunnelDropoffDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffCauseRow;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FunnelRcaServiceImplTest {

  private static final String PROJECT = "proj-1";

  @Mock FunnelDefinitionDao funnelDefinitionDao;
  @Mock FunnelDropoffDao funnelDropoffDao;

  FunnelRcaServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new FunnelRcaServiceImpl(funnelDefinitionDao, funnelDropoffDao);
  }

  private FunnelDefinitionRow funnel(long id, String mode, int stepCount, String stepsJson) {
    if (stepsJson == null) {
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < stepCount; i++) {
        if (i > 0) {
          sb.append(",");
        }
        sb.append("{\"eventName\":\"step-").append(i).append("\"}");
      }
      sb.append("]");
      stepsJson = sb.toString();
    }
    return FunnelDefinitionRow.builder()
        .id(id)
        .projectId(PROJECT)
        .name("f")
        .description(null)
        .funnelType("AUTO")
        .stepOrderType("ORDERED")
        .stepsJson(stepsJson)
        .windowSeconds(86400L)
        .mode(mode)
        .filtersJson(null)
        .dateRangeDays(7)
        .build();
  }

  @Nested
  class GetFunnelRootCause {

    @Test
    void shouldRejectNegativeFocusStepIndex() {
      assertThatThrownBy(() -> service.getFunnelRootCause(PROJECT, 1L, -1, null).blockingGet())
          .isInstanceOf(WebApplicationException.class);
      verify(funnelDefinitionDao, never()).findByProjectAndId(eq(PROJECT), eq(1L));
    }

    @Test
    void shouldBubbleNotFoundWhenFunnelMissing() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(99L))).thenReturn(Maybe.empty());

      assertThatThrownBy(() -> service.getFunnelRootCause(PROJECT, 99L, 0, null).blockingGet())
          .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void shouldRejectFocusStepIndexOutOfRange() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(1L)))
          .thenReturn(Maybe.just(funnel(1L, "SESSIONS", 2, null)));

      assertThatThrownBy(() -> service.getFunnelRootCause(PROJECT, 1L, 2, null).blockingGet())
          .isInstanceOf(WebApplicationException.class);
      verify(funnelDropoffDao, never())
          .queryCausesFromAttribution(eq(PROJECT), eq(1L), eq(2), eq(null));
    }

    @Test
    void shouldTreatMalformedStepsJsonAsEmptySteps() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(1L)))
          .thenReturn(Maybe.just(funnel(1L, "SESSIONS", 0, "not-json")));

      assertThatThrownBy(() -> service.getFunnelRootCause(PROJECT, 1L, 0, null).blockingGet())
          .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void shouldReturnNoDataWhenAttributionEmpty() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(1L)))
          .thenReturn(Maybe.just(funnel(1L, "UNIQUE_USERS", 3, null)));
      when(funnelDropoffDao.queryCausesFromAttribution(
              eq(PROJECT), eq(1L), eq(1), eq("2026-05-01")))
          .thenReturn(Single.just(Collections.emptyList()));

      RootCauseResult result =
          service.getFunnelRootCause(PROJECT, 1L, 1, "2026-05-01").blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getBaseline().get("focus_step_name")).isEqualTo("step-1");
      assertThat(result.getBaseline().get("funnel_mode")).isEqualTo("UNIQUE_USERS");
    }

    @Test
    void shouldMapAttributionRowsToRootCauseResult() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(7L)))
          .thenReturn(Maybe.just(funnel(7L, "SESSIONS", 2, null)));
      FunnelDropoffCauseRow cause =
          FunnelDropoffCauseRow.builder()
              .causeKind("http_5xx")
              .causeLabel("503 @ checkout")
              .dropoffCohort(100L)
              .dropoffAffected(25L)
              .converterCohort(80L)
              .converterAffected(2L)
              .lift(8.0)
              .exampleSessions("sess-a")
              .build();
      when(funnelDropoffDao.queryCausesFromAttribution(eq(PROJECT), eq(7L), eq(0), eq(null)))
          .thenReturn(Single.just(List.of(cause)));

      RootCauseResult result = service.getFunnelRootCause(PROJECT, 7L, 0, null).blockingGet();

      assertThat(result.getNoDataAvailable()).isFalse();
      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("503 @ checkout");
      assertThat(result.getBaseline().get("dropoff_rate_pct")).isEqualTo(25.0);
    }
  }
}

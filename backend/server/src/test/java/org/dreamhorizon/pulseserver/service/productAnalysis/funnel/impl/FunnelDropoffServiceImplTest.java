package org.dreamhorizon.pulseserver.service.productAnalysis.funnel.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffEvidenceRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffEvidenceResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FunnelDropoffServiceImplTest {

  private static final String PROJECT = "proj-1";

  @Mock FunnelDefinitionDao funnelDefinitionDao;
  @Mock FunnelDropoffDao funnelDropoffDao;

  FunnelDropoffServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new FunnelDropoffServiceImpl(funnelDefinitionDao, funnelDropoffDao);
  }

  private FunnelDefinitionRow funnel(long id, String mode, int stepCount) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < stepCount; i++) {
      if (i > 0) sb.append(",");
      sb.append("{\"eventName\":\"step-").append(i).append("\"}");
    }
    sb.append("]");
    return FunnelDefinitionRow.builder()
        .id(id).projectId(PROJECT).name("f").description(null)
        .funnelType("AUTO").stepOrderType("ORDERED")
        .stepsJson(sb.toString()).windowSeconds(86400L).mode(mode)
        .filtersJson(null).dateRangeDays(7)
        .build();
  }

  @Nested
  class GetDropoff {
    @Test
    void shouldRejectNegativeStepIndex() {
      assertThatThrownBy(() ->
          service.getDropoff(PROJECT, 1L, -1, null).blockingGet())
          .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void shouldRejectStepIndexOutOfRange() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(1L)))
          .thenReturn(Maybe.just(funnel(1L, "SESSIONS", 3)));
      assertThatThrownBy(() ->
          service.getDropoff(PROJECT, 1L, 5, null).blockingGet())
          .isInstanceOf(WebApplicationException.class);
      verify(funnelDropoffDao, never())
          .queryCauses(anyString(), anyLong(), anyInt(), any(), any());
    }

    @Test
    void shouldBubbleNotFoundWhenFunnelMissing() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(99L)))
          .thenReturn(Maybe.empty());
      assertThatThrownBy(() ->
          service.getDropoff(PROJECT, 99L, 0, null).blockingGet())
          .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void shouldAssembleResponseWithCohortsFromFirstCause() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(1L)))
          .thenReturn(Maybe.just(funnel(1L, "UNIQUE_USERS", 3)));
      FunnelDropoffCauseRow causeRow = FunnelDropoffCauseRow.builder()
          .causeKind("crash").causeKey("k").causeLabel("l")
          .dropoffCohort(50L).dropoffAffected(10L)
          .converterCohort(100L).converterAffected(1L)
          .lift(20.0).exampleSessions("s-1,s-2").build();
      when(funnelDropoffDao.queryCauses(
          eq(PROJECT), eq(1L), eq(1), any(), eq("UNIQUE_USERS")))
          .thenReturn(Single.just(List.of(causeRow)));

      FunnelDropoffResponse resp =
          service.getDropoff(PROJECT, 1L, 1, "2026-04-23").blockingGet();

      assertThat(resp.getFunnelId()).isEqualTo(1L);
      assertThat(resp.getStepIndex()).isEqualTo(1);
      assertThat(resp.getStepName()).isEqualTo("step-1");
      assertThat(resp.getMode()).isEqualTo("UNIQUE_USERS");
      assertThat(resp.getDropoffCohort()).isEqualTo(50L);
      assertThat(resp.getConverterCohort()).isEqualTo(100L);
      assertThat(resp.getCauses()).hasSize(1);
      assertThat(resp.getCauses().get(0).getExampleSessionIds())
          .containsExactly("s-1", "s-2");
    }

    @Test
    void shouldReturnEmptyCohortsWhenNoCauses() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(1L)))
          .thenReturn(Maybe.just(funnel(1L, "SESSIONS", 3)));
      when(funnelDropoffDao.queryCauses(
          eq(PROJECT), eq(1L), eq(0), any(), eq("SESSIONS")))
          .thenReturn(Single.just(Collections.emptyList()));

      FunnelDropoffResponse resp =
          service.getDropoff(PROJECT, 1L, 0, null).blockingGet();
      assertThat(resp.getDropoffCohort()).isZero();
      assertThat(resp.getConverterCohort()).isZero();
      assertThat(resp.getCauses()).isEmpty();
    }
  }

  @Nested
  class GetEvidence {
    @Test
    void shouldShortCircuitForEmptyIds() {
      FunnelDropoffEvidenceResponse resp =
          service.getEvidence(PROJECT, 1L, 0, null, Collections.emptyList()).blockingGet();
      assertThat(resp.getExamples()).isEmpty();
      verify(funnelDefinitionDao, never()).findByProjectAndId(any(), anyLong());
    }

    @Test
    void shouldBubbleNotFoundWhenFunnelMissing() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(1L)))
          .thenReturn(Maybe.empty());
      assertThatThrownBy(() ->
          service.getEvidence(PROJECT, 1L, 0, null, List.of("s-1")).blockingGet())
          .isInstanceOf(WebApplicationException.class);
    }

    @Test
    void shouldMapEvidenceRowsToResponse() {
      when(funnelDefinitionDao.findByProjectAndId(eq(PROJECT), eq(1L)))
          .thenReturn(Maybe.just(funnel(1L, "SESSIONS", 2)));
      FunnelDropoffEvidenceRow row = FunnelDropoffEvidenceRow.builder()
          .sessionId("s-1").userId("u-1").lastReachedAt("2026-04-23")
          .traceId("t-1").screen("Home").appVersion("1.0").platform("ios")
          .build();
      when(funnelDropoffDao.queryEvidence(
          eq(PROJECT), eq(1L), eq(0), any(), eq("SESSIONS"), eq(List.of("s-1"))))
          .thenReturn(Single.just(List.of(row)));

      FunnelDropoffEvidenceResponse resp =
          service.getEvidence(PROJECT, 1L, 0, null, List.of("s-1")).blockingGet();
      assertThat(resp.getExamples()).hasSize(1);
      assertThat(resp.getExamples().get(0).getSessionId()).isEqualTo("s-1");
    }
  }
}

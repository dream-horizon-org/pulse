package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffCauseRow;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.junit.jupiter.api.Test;

class FunnelRcaMapperTest {

  @Test
  void shouldMarkNoDataWhenCausesEmpty() {
    RootCauseResult result =
        FunnelRcaMapper.toRootCauseResult(List.of(), "SESSIONS", "checkout", 1, 42L);

    assertThat(result.getNoDataAvailable()).isTrue();
    assertThat(result.getSegments()).isEmpty();
    assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
  }

  @Test
  void shouldOrderSegmentsByLiftDescending() {
    FunnelDropoffCauseRow low =
        FunnelDropoffCauseRow.builder()
            .causeKind("frozen_frame")
            .causeLabel("Frozen frames")
            .dropoffCohort(100L)
            .dropoffAffected(10L)
            .converterCohort(80L)
            .converterAffected(2L)
            .lift(2.0)
            .exampleSessions("s-low")
            .build();
    FunnelDropoffCauseRow high =
        FunnelDropoffCauseRow.builder()
            .causeKind("http_5xx")
            .causeLabel("503 @ checkout")
            .dropoffCohort(100L)
            .dropoffAffected(30L)
            .converterCohort(80L)
            .converterAffected(1L)
            .lift(9.5)
            .exampleSessions("s-high")
            .build();

    RootCauseResult result =
        FunnelRcaMapper.toRootCauseResult(
            List.of(low, high), "UNIQUE_USERS", "checkout", 2, 7L);

    assertThat(result.getNoDataAvailable()).isFalse();
    assertThat(result.getSegments()).hasSize(2);
    assertThat(result.getSegments().get(0).getLabel()).isEqualTo("503 @ checkout");
    assertThat(result.getSegments().get(0).getExampleSessionIds()).containsExactly("s-high");
    assertThat(result.getBaseline().get("funnel_mode")).isEqualTo("UNIQUE_USERS");
  }

  @Test
  void shouldUseCauseKindWhenLabelMissing() {
    FunnelDropoffCauseRow row =
        FunnelDropoffCauseRow.builder()
            .causeKind("frozen_frame")
            .dropoffCohort(10L)
            .dropoffAffected(2L)
            .converterCohort(5L)
            .converterAffected(1L)
            .lift(1.0)
            .build();

    RootCauseResult result =
        FunnelRcaMapper.toRootCauseResult(List.of(row), "SESSIONS", "s", 0, 1L);

    assertThat(result.getSegments().get(0).getLabel()).isEqualTo("frozen_frame");
    assertThat(result.getSegments().get(0).getDimensions()).containsEntry("cause_kind", "frozen_frame");
  }

  @Test
  void shouldLimitSegmentsToEightHighestLift() {
    List<FunnelDropoffCauseRow> causes = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      causes.add(
          FunnelDropoffCauseRow.builder()
              .causeKind("k" + i)
              .causeLabel("label-" + i)
              .dropoffCohort(100L)
              .dropoffAffected(10L)
              .converterCohort(50L)
              .converterAffected(1L)
              .lift((double) i)
              .build());
    }

    RootCauseResult result =
        FunnelRcaMapper.toRootCauseResult(causes, "SESSIONS", "s", 0, 1L);

    assertThat(result.getSegments()).hasSize(8);
    assertThat(result.getSegments().get(0).getLabel()).isEqualTo("label-9");
  }

  @Test
  void shouldReturnZeroDropoffRateWhenCohortIsZero() {
    FunnelDropoffCauseRow row =
        FunnelDropoffCauseRow.builder()
            .causeLabel("x")
            .dropoffCohort(0L)
            .dropoffAffected(5L)
            .converterCohort(0L)
            .converterAffected(1L)
            .lift(null)
            .build();

    RootCauseResult result =
        FunnelRcaMapper.toRootCauseResult(List.of(row), null, null, 2, 3L);

    assertThat(result.getBaseline().get("dropoff_rate_pct")).isEqualTo(0.0);
    assertThat(result.getSegments().get(0).getMetrics().get("dropoff_rate_pct")).isEqualTo(0.0);
  }

  @Test
  void shouldHonorCustomNoDataMessage() {
    RootCauseResult result =
        FunnelRcaMapper.noDataUnavailable(1L, 0, "checkout", "SESSIONS", "custom reason");

    assertThat(result.getNoDataAvailable()).isTrue();
    assertThat(result.getMessage()).isEqualTo("custom reason");
  }

  @Test
  void shouldMarkNoDataWhenCausesNull() {
    RootCauseResult result = FunnelRcaMapper.toRootCauseResult(null, "SESSIONS", "s", 0, 1L);

    assertThat(result.getNoDataAvailable()).isTrue();
  }
}

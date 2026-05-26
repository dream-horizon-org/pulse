package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import static org.assertj.core.api.Assertions.assertThat;

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
}

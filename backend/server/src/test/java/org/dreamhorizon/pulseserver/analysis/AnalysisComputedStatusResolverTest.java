package org.dreamhorizon.pulseserver.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;
import org.junit.jupiter.api.Test;

class AnalysisComputedStatusResolverTest {

  @Test
  void shouldReturnInProgressWhenJobPendingOrRunning() {
    assertThat(AnalysisComputedStatusResolver.compute(FunnelType.AUTO, "PENDING"))
      .isEqualTo(AnalysisComputedStatus.IN_PROGRESS);
    assertThat(AnalysisComputedStatusResolver.compute(FunnelType.ONCE, "running"))
      .isEqualTo(AnalysisComputedStatus.IN_PROGRESS);
  }

  @Test
  void shouldReturnPendingForOnceWithNoJob() {
    assertThat(AnalysisComputedStatusResolver.compute(FunnelType.ONCE, null))
      .isEqualTo(AnalysisComputedStatus.PENDING);
  }

  @Test
  void shouldReturnActiveForAutoWithNoJob() {
    assertThat(AnalysisComputedStatusResolver.compute(FunnelType.AUTO, null))
      .isEqualTo(AnalysisComputedStatus.ACTIVE);
  }

  @Test
  void shouldMapFailedAndSucceeded() {
    assertThat(AnalysisComputedStatusResolver.compute(FunnelType.AUTO, "FAILED"))
      .isEqualTo(AnalysisComputedStatus.WARN);
    assertThat(AnalysisComputedStatusResolver.compute(FunnelType.ONCE, "FAILED"))
      .isEqualTo(AnalysisComputedStatus.FAILED);
    assertThat(AnalysisComputedStatusResolver.compute(FunnelType.AUTO, "SUCCEEDED"))
      .isEqualTo(AnalysisComputedStatus.ACTIVE);
    assertThat(AnalysisComputedStatusResolver.compute(FunnelType.ONCE, "SUCCEEDED"))
      .isEqualTo(AnalysisComputedStatus.COMPLETED);
  }
}

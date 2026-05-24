package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.AnalysisBasis;
import org.junit.jupiter.api.Test;

class AnalyticsSignalSourceTest {

  @Test
  void forBasis_eventUsesLogsCustomEvent() {
    AnalyticsSignalSource source = AnalyticsSignalSource.forBasis(AnalysisBasis.EVENT);
    assertThat(source.getTable()).isEqualTo("otel.otel_logs");
    assertThat(source.getPulseType()).isEqualTo("custom_event");
    assertThat(source.getStepColumn()).isEqualTo("EventName");
    assertThat(source.isScreen()).isFalse();
  }

  @Test
  void forBasis_screenUsesTracesScreenLoad() {
    AnalyticsSignalSource source = AnalyticsSignalSource.forBasis(AnalysisBasis.SCREEN);
    assertThat(source.getTable()).isEqualTo("otel.otel_traces");
    assertThat(source.getPulseType()).isEqualTo("screen_load");
    assertThat(source.getStepColumn()).isEqualTo("ScreenName");
    assertThat(source.isScreen()).isTrue();
    assertThat(source.nonEmptyStepFilter()).contains("ScreenName != ''");
  }
}

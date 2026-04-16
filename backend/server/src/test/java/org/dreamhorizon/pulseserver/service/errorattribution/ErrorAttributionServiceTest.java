package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErrorAttributionServiceTest {

  private static final String PROJECT = "p1";
  private static final String INTERACTION = "tap_pay";
  private static final Instant START = Instant.parse("2026-04-01T00:00:00Z");
  private static final Instant END = Instant.parse("2026-04-08T15:00:00Z");

  @Mock private RootCauseConfig rootCauseConfig;
  @Mock private ErrorAttributionDrillDownService errorAttributionDrillDownService;

  private ErrorAttributionService service;

  @BeforeEach
  void setUp() {
    lenient()
        .when(rootCauseConfig.getMinRiskRatioForIssueAttribution())
        .thenReturn(RootCauseConfig.DEFAULT_MIN_RISK_RATIO_FOR_ISSUE_ATTRIBUTION);
    service = new ErrorAttributionService(rootCauseConfig, errorAttributionDrillDownService);
  }

  @Nested
  class DrillDownHttpPath {

    @Test
    void shouldRunDrillQueriesOnlyWithoutSummaryWhenDrillSignalsPresent() {
      when(errorAttributionDrillDownService.getDrillDown(
              eq(PROJECT),
              eq(INTERACTION),
              any(Instant.class),
              any(Instant.class),
              eq(ErrorAttributionDrillDownSignal.crash)))
          .thenReturn(
              Single.just(
                  ErrorAttributionDrillDownResult.builder().signal("crash").issues(List.of()).build()));

      ErrorAttributionWithDrillDown bundle =
          service
              .getErrorAttributionWithOptionalDrillDown(
                  PROJECT, INTERACTION, START, END, List.of(ErrorAttributionDrillDownSignal.crash))
              .blockingGet();

      assertThat(bundle.relatedAttributions()).isNotNull();
      verify(errorAttributionDrillDownService)
          .getDrillDown(
              eq(PROJECT),
              eq(INTERACTION),
              any(Instant.class),
              any(Instant.class),
              eq(ErrorAttributionDrillDownSignal.crash));
    }

    @Test
    void shouldFailWhenDrillSignalsEmpty() {
      assertThatThrownBy(
              () ->
                  service
                      .getErrorAttributionWithOptionalDrillDown(
                          PROJECT, INTERACTION, START, END, List.of())
                      .blockingGet())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("drillSignals must not be empty");

      verify(errorAttributionDrillDownService, never())
          .getDrillDown(anyString(), anyString(), any(Instant.class), any(Instant.class), any());
    }
  }
}

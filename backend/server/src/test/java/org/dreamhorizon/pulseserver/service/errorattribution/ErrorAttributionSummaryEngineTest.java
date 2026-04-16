package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult.RiskRatioRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErrorAttributionSummaryEngineTest {

  @Mock private RootCauseConfig rootCauseConfig;

  private ErrorAttributionSummaryEngine engine;

  @BeforeEach
  void setUp() {
    lenient()
        .when(rootCauseConfig.getMinPoorSessionsForErrorAttribution())
        .thenReturn(RootCauseConfig.DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION);
    engine = new ErrorAttributionSummaryEngine(rootCauseConfig);
  }

  private static Map<String, Object> baseCountsRow() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("n_u", 2000L);
    m.put("n_poor_u", 1500L);
    m.put("n_treated_crash", 0L);
    m.put("n_control_crash", 2000L);
    m.put("n_treated_low_crash", 0L);
    m.put("n_control_low_crash", 100L);
    m.put("n_treated_anr", 0L);
    m.put("n_control_anr", 2000L);
    m.put("n_treated_low_anr", 0L);
    m.put("n_control_low_anr", 100L);
    m.put("n_treated_nf", 0L);
    m.put("n_control_nf", 2000L);
    m.put("n_treated_low_nf", 0L);
    m.put("n_control_low_nf", 100L);
    m.put("n_treated_api", 0L);
    m.put("n_control_api", 2000L);
    m.put("n_treated_low_api", 0L);
    m.put("n_control_low_api", 100L);
    return m;
  }

  @Nested
  class EmptyAggregate {

    @Test
    void shouldReturnInsufficientDataWithEmptyTreatedArmsWhenNoRows() {
      ErrorAttributionResult r = engine.buildFromAggregateRows(List.of());

      assertThat(r.getNU()).isZero();
      assertThat(r.getNPoorInU()).isZero();
      assertThat(r.getTrackBInsufficientData()).isTrue();
      assertThat(r.getJointWinners()).isNull();
      assertThat(r.getRiskRatios()).hasSize(4);
      assertThat(r.getRiskRatios().get(0).getRrUndefinedReason())
          .isEqualTo(ErrorAttributionResult.RR_EMPTY_TREATED_ARM);
      assertThat(r.getRiskRatios().get(0).getP1()).isNull();
      assertThat(r.getCachedAt()).isNull();
      assertThat(r.getDisclaimer()).isEqualTo(ErrorAttributionService.DISCLAIMER);
    }
  }

  @Nested
  class RiskRatioMatrix {

    @Test
    void shouldClassifyEmptyControlBeforeInfiniteRr() {
      Map<String, Object> row = baseCountsRow();
      row.put("n_treated_crash", 5L);
      row.put("n_control_crash", 0L);
      row.put("n_treated_low_crash", 3L);
      row.put("n_control_low_crash", 0L);

      ErrorAttributionResult r = engine.buildFromAggregateRows(List.of(row));

      RiskRatioRow crash = r.getRiskRatios().get(0);
      assertThat(crash.getRrUndefinedReason()).isEqualTo(ErrorAttributionResult.RR_EMPTY_CONTROL_ARM);
    }

    @Test
    void shouldEmitInfiniteRrReasonWhenP2ZeroAndP1Positive() {
      Map<String, Object> row = baseCountsRow();
      row.put("n_treated_crash", 10L);
      row.put("n_control_crash", 4L);
      row.put("n_treated_low_crash", 2L);
      row.put("n_control_low_crash", 0L);

      ErrorAttributionResult r = engine.buildFromAggregateRows(List.of(row));

      RiskRatioRow crash = r.getRiskRatios().get(0);
      assertThat(crash.getRrUndefinedReason()).isEqualTo(ErrorAttributionResult.RR_INFINITE_RR);
      assertThat(crash.getRr()).isNull();
      assertThat(r.getJointWinners()).containsExactly("crash");
    }

    @Test
    void shouldTieJointWinnersAfterFourDpRounding() {
      Map<String, Object> row = baseCountsRow();
      row.put("n_treated_crash", 100L);
      row.put("n_control_crash", 100L);
      row.put("n_treated_low_crash", 12L);
      row.put("n_control_low_crash", 10L);
      row.put("n_treated_anr", 200L);
      row.put("n_control_anr", 200L);
      row.put("n_treated_low_anr", 24L);
      row.put("n_control_low_anr", 20L);

      ErrorAttributionResult r = engine.buildFromAggregateRows(List.of(row));

      assertThat(r.getNPoorInU()).isGreaterThanOrEqualTo(1000L);
      assertThat(r.getJointWinners()).containsExactlyInAnyOrder("crash", "anr");
    }

    @Test
    void shouldOmitJointWinnersWhenPoorGateFails() {
      Map<String, Object> row = baseCountsRow();
      row.put("n_poor_u", 10L);
      row.put("n_treated_crash", 100L);
      row.put("n_control_crash", 100L);
      row.put("n_treated_low_crash", 50L);
      row.put("n_control_low_crash", 10L);

      ErrorAttributionResult r = engine.buildFromAggregateRows(List.of(row));

      assertThat(r.getTrackBInsufficientData()).isTrue();
      assertThat(r.getJointWinners()).isNull();
      assertThat(r.getMinPoorSessionsForErrorAttribution())
          .isEqualTo(RootCauseConfig.DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION);
    }
  }
}

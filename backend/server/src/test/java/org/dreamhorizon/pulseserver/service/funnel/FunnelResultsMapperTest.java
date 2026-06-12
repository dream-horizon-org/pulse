package org.dreamhorizon.pulseserver.service.funnel;

import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelResultRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelResultsResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelStepMeasureResult;
import org.dreamhorizon.pulseserver.service.productAnalysis.funnel.FunnelResultsMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FunnelResultsMapperTest {

  @Nested
  class FromRows {

    @Test
    void shouldReturnZerosWhenNullOrEmpty() {
      assertThat(FunnelResultsMapper.fromRows(null).getTotalEnteredUsers()).isZero();
      assertThat(FunnelResultsMapper.fromRows(List.of()).getSteps()).isEmpty();
    }

    @Test
    void shouldSetLastRunAtFromFirstRow() {
      Instant runTime = Instant.parse("2026-05-01T10:00:00Z");
      List<FunnelResultRow> rows = List.of(
        FunnelResultRow.builder()
          .stepIndex(0).stepName("A").userCount(100L).conversionPct(100.0)
          .runTime(runTime).build());

      assertThat(FunnelResultsMapper.fromRows(rows).getLastRunAt()).isEqualTo(runTime);
    }

    @Test
    void shouldHaveNullLastRunAtWhenEmpty() {
      assertThat(FunnelResultsMapper.fromRows(List.of()).getLastRunAt()).isNull();
    }

    @Test
    void shouldMapStepsAndDropoffFromPreviousStep() {
      List<FunnelResultRow> rows =
        List.of(
          FunnelResultRow.builder()
            .stepIndex(0)
            .stepName("A")
            .userCount(8750L)
            .conversionPct(100.0)
            .build(),
          FunnelResultRow.builder()
            .stepIndex(1)
            .stepName("B")
            .userCount(6820L)
            .conversionPct(77.9)
            .build());

      FunnelResultsResponse out = FunnelResultsMapper.fromRows(rows);

      assertThat(out.getTotalEnteredUsers()).isEqualTo(8750L);
      assertThat(out.getOverallConversionRate()).isEqualTo(77.9);

      List<FunnelStepMeasureResult> steps = out.getSteps();
      assertThat(steps).hasSize(2);
      assertThat(steps.get(0).getDropoffRate()).isZero();
      assertThat(steps.get(1).getDropoffRate()).isEqualTo(22.1);
    }
  }
}

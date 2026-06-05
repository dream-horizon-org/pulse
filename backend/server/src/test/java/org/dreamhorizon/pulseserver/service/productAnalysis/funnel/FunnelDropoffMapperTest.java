package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffCauseRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffEvidenceRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffCauseDto;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDropoffEvidenceDto;
import org.junit.jupiter.api.Test;

class FunnelDropoffMapperTest {

  @Test
  void shouldReturnEmptyListForNullOrEmptyCauseRows() {
    assertThat(FunnelDropoffMapper.fromCauseRows(null)).isEmpty();
    assertThat(FunnelDropoffMapper.fromCauseRows(List.of())).isEmpty();
  }

  @Test
  void shouldMapCauseRowFieldsAndSplitExamples() {
    FunnelDropoffCauseRow row = FunnelDropoffCauseRow.builder()
        .causeKind("crash").causeKey("NPE@Checkout").causeLabel("NPE @ Checkout")
        .dropoffCohort(200L).dropoffAffected(50L)
        .converterCohort(100L).converterAffected(5L)
        .lift(5.0).exampleSessions("s-1, s-2 ,s-3,,")
        .build();
    List<FunnelDropoffCauseDto> out = FunnelDropoffMapper.fromCauseRows(List.of(row));
    assertThat(out).hasSize(1);
    FunnelDropoffCauseDto dto = out.get(0);
    assertThat(dto.getCauseKind()).isEqualTo("crash");
    assertThat(dto.getDropoffCohort()).isEqualTo(200L);
    assertThat(dto.getDropoffAffected()).isEqualTo(50L);
    assertThat(dto.getDropoffRate()).isEqualTo(25.0); // 50/200 * 100
    assertThat(dto.getExampleSessionIds()).containsExactly("s-1", "s-2", "s-3");
  }

  @Test
  void shouldHandleNullNumericsAndCsv() {
    FunnelDropoffCauseRow row = FunnelDropoffCauseRow.builder()
        .causeKind("anr").causeKey("k").causeLabel("l")
        .dropoffCohort(null).dropoffAffected(null)
        .converterCohort(null).converterAffected(null)
        .lift(null).exampleSessions(null)
        .build();
    FunnelDropoffCauseDto dto = FunnelDropoffMapper.fromCauseRows(List.of(row)).get(0);
    assertThat(dto.getDropoffCohort()).isZero();
    assertThat(dto.getDropoffAffected()).isZero();
    assertThat(dto.getConverterCohort()).isZero();
    assertThat(dto.getConverterAffected()).isZero();
    assertThat(dto.getLift()).isZero();
    assertThat(dto.getDropoffRate()).isZero();
    assertThat(dto.getExampleSessionIds()).isEmpty();
  }

  @Test
  void shouldMapEvidenceRowsOneToOne() {
    FunnelDropoffEvidenceRow r = FunnelDropoffEvidenceRow.builder()
        .sessionId("s-1").userId("u-1").lastReachedAt("2026-04-23 10:00:00")
        .traceId("t-1").screen("Checkout").appVersion("1.2.3").platform("android")
        .build();
    List<FunnelDropoffEvidenceDto> out = FunnelDropoffMapper.fromEvidenceRows(List.of(r));
    assertThat(out).hasSize(1);
    assertThat(out.get(0).getSessionId()).isEqualTo("s-1");
    assertThat(out.get(0).getTraceId()).isEqualTo("t-1");
    assertThat(out.get(0).getScreen()).isEqualTo("Checkout");
  }
}

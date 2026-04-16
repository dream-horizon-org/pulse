package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.IssueRow;
import org.junit.jupiter.api.Test;

class ErrorAttributionRelatedAttributionsTest {

  @Test
  void mergeFiltersByMinRrAndAppliesGlobalLimit() {
    RootCauseConfig cfg =
        RootCauseConfig.withDefaults(
            RootCauseConfig.builder()
                .issueDrillDownLimit(1)
                .minRiskRatioForIssueAttribution(2.0d)
                .build());
    IssueRow strong =
        IssueRow.builder()
            .groupId("g1")
            .title("T1")
            .occurrences(10L)
            .nTreated(10L)
            .nControl(100L)
            .nTreatedLow(5L)
            .nControlLow(10L)
            .p1(0.5)
            .p2(0.1)
            .rr(5.0)
            .rrUndefined(false)
            .rrUndefinedReason(null)
            .build();
    IssueRow weak =
        IssueRow.builder()
            .groupId("g2")
            .title("T2")
            .occurrences(10L)
            .nTreated(10L)
            .nControl(100L)
            .nTreatedLow(1L)
            .nControlLow(10L)
            .p1(0.1)
            .p2(0.1)
            .rr(1.0)
            .rrUndefined(false)
            .rrUndefinedReason(null)
            .build();
    Map<String, ErrorAttributionDrillDownResult> by = new LinkedHashMap<>();
    by.put(
        "crash",
        ErrorAttributionDrillDownResult.builder()
            .signal("crash")
            .issues(List.of(strong, weak))
            .build());
    List<ErrorAttributionRelatedAttributionRow> out =
        ErrorAttributionRelatedAttributions.buildMerged(by, cfg);
    assertThat(out).hasSize(1);
    assertThat(out.get(0).groupId()).isEqualTo("g1");
  }
}

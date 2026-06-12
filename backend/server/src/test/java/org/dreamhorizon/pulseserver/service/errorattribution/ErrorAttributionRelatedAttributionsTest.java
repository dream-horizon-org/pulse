package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.IssueRow;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.NetworkEndpointRow;
import org.junit.jupiter.api.Test;

class ErrorAttributionRelatedAttributionsTest {

  @Test
  void mergeReturnsEmptyWhenBySignalNullOrEmpty() {
    RootCauseConfig cfg = RootCauseConfig.withDefaults(RootCauseConfig.builder().build());
    assertThat(ErrorAttributionRelatedAttributions.buildMerged(null, cfg)).isEmpty();
    assertThat(ErrorAttributionRelatedAttributions.buildMerged(Map.of(), cfg)).isEmpty();
  }

  @Test
  void mergeSkipsNullDrillDownResult() {
    RootCauseConfig cfg =
        RootCauseConfig.withDefaults(
            RootCauseConfig.builder().issueDrillDownLimit(10).minRiskRatioForIssueAttribution(0.0d).build());
    Map<String, ErrorAttributionDrillDownResult> by = new LinkedHashMap<>();
    by.put("crash", null);
    by.put(
        "anr",
        ErrorAttributionDrillDownResult.builder()
            .signal("anr")
            .issues(
                List.of(
                    IssueRow.builder()
                        .groupId("g")
                        .title("t")
                        .occurrences(1L)
                        .nTreated(1L)
                        .nControl(10L)
                        .nTreatedLow(1L)
                        .nControlLow(2L)
                        .p1(0.1)
                        .p2(0.05)
                        .rr(2.0)
                        .rrUndefined(false)
                        .rrUndefinedReason(null)
                        .build()))
            .build());
    List<ErrorAttributionRelatedAttributionRow> out = ErrorAttributionRelatedAttributions.buildMerged(by, cfg);
    assertThat(out).hasSize(1);
    assertThat(out.get(0).sourceSignal()).isEqualTo("anr");
  }

  @Test
  void mergeIncludesApiEndpointsPassingThreshold() {
    RootCauseConfig cfg =
        RootCauseConfig.withDefaults(
            RootCauseConfig.builder().issueDrillDownLimit(10).minRiskRatioForIssueAttribution(1.5d).build());
    NetworkEndpointRow ep =
        NetworkEndpointRow.builder()
            .url("/v1/x")
            .graphqlOperationName(null)
            .graphqlOperationType(null)
            .httpMethod("POST")
            .httpStatusCode("503")
            .occurrences(5L)
            .nTreated(5L)
            .nControl(100L)
            .nTreatedLow(2L)
            .nControlLow(20L)
            .p1(0.4)
            .p2(0.1)
            .rr(4.0)
            .rrUndefined(false)
            .rrUndefinedReason(null)
            .build();
    Map<String, ErrorAttributionDrillDownResult> by = new LinkedHashMap<>();
    by.put(
        "api",
        ErrorAttributionDrillDownResult.builder().signal("api").issues(null).networkEndpoints(List.of(ep)).build());
    List<ErrorAttributionRelatedAttributionRow> out = ErrorAttributionRelatedAttributions.buildMerged(by, cfg);
    assertThat(out).hasSize(1);
    assertThat(out.get(0).rowKind()).isEqualTo(ErrorAttributionRelatedAttributions.ROW_KIND_API);
    assertThat(out.get(0).url()).isEqualTo("/v1/x");
    assertThat(out.get(0).httpMethod()).isEqualTo("POST");
    assertThat(out.get(0).httpStatusCode()).isEqualTo("503");
  }

  @Test
  void mergeSortsTieBreakBySignalRankCrashBeforeAnr() {
    RootCauseConfig cfg =
        RootCauseConfig.withDefaults(
            RootCauseConfig.builder().issueDrillDownLimit(10).minRiskRatioForIssueAttribution(0.0d).build());
    IssueRow sameRr =
        IssueRow.builder()
            .groupId("g")
            .title("t")
            .occurrences(10L)
            .nTreated(10L)
            .nControl(100L)
            .nTreatedLow(5L)
            .nControlLow(10L)
            .p1(0.5)
            .p2(0.1)
            .rr(3.0)
            .rrUndefined(false)
            .rrUndefinedReason(null)
            .build();
    Map<String, ErrorAttributionDrillDownResult> by = new LinkedHashMap<>();
    by.put(
        "anr",
        ErrorAttributionDrillDownResult.builder().signal("anr").issues(List.of(sameRr)).build());
    by.put(
        "crash",
        ErrorAttributionDrillDownResult.builder().signal("crash").issues(List.of(sameRr)).build());
    List<ErrorAttributionRelatedAttributionRow> out = ErrorAttributionRelatedAttributions.buildMerged(by, cfg);
    assertThat(out).hasSize(2);
    assertThat(out.get(0).sourceSignal()).isEqualTo("crash");
    assertThat(out.get(1).sourceSignal()).isEqualTo("anr");
  }

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

  @Test
  void mergeFiltersRowBelowTreatedPrevalenceEvenWhenStrongRr() {
    IssueRow flashyButRare =
        IssueRow.builder()
            .groupId("gRare")
            .title("Volatile")
            .occurrences(5L)
            .nTreated(5L)
            .nControl(99_995L)
            .nTreatedLow(3L)
            .nControlLow(10_000L)
            .p1(0.6)
            .p2(0.1)
            .rr(6.0)
            .rrUndefined(false)
            .rrUndefinedReason(null)
            .build();
    RootCauseConfig cfg =
        RootCauseConfig.withDefaults(
            RootCauseConfig.builder()
                .issueDrillDownLimit(10)
                .minRiskRatioForIssueAttribution(2.0d)
                .minTreatedPrevalenceFractionInU(
                    RootCauseConfig.DEFAULT_MIN_TREATED_PREVALENCE_FRACTION_IN_U)
                .build());
    Map<String, ErrorAttributionDrillDownResult> by = new LinkedHashMap<>();
    by.put(
        "non_fatal",
        ErrorAttributionDrillDownResult.builder()
            .signal("non_fatal")
            .issues(List.of(flashyButRare))
            .build());
    assertThat(ErrorAttributionRelatedAttributions.buildMerged(by, cfg)).isEmpty();
  }
}

package org.dreamhorizon.pulseserver.service.errorattribution;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.resources.interaction.models.ErrorAttributionRestResponse;

/** Maps Track B domain rows to the REST wire DTO (shared by {@code InteractionController} and RCA merge). */
public final class ErrorAttributionRestResponseMapper {

  private ErrorAttributionRestResponseMapper() {}

  public static ErrorAttributionRestResponse fromBundle(ErrorAttributionWithDrillDown bundle) {
    return fromRows(bundle.relatedAttributions(), bundle.minRiskRatioForIssueAttribution());
  }

  public static ErrorAttributionRestResponse fromRows(
      List<ErrorAttributionRelatedAttributionRow> relatedAttributions,
      Double minRiskRatioForIssueAttribution) {
    List<ErrorAttributionRestResponse.RelatedAttributionEntry> related =
        relatedAttributions == null || relatedAttributions.isEmpty()
            ? List.of()
            : relatedAttributions.stream()
                .map(ErrorAttributionRestResponseMapper::toRelatedAttributionEntry)
                .collect(Collectors.toList());
    return ErrorAttributionRestResponse.builder()
        .disclaimer(ErrorAttributionService.DISCLAIMER)
        .cachedAt(Instant.now())
        .minRiskRatioForIssueAttribution(minRiskRatioForIssueAttribution)
        .relatedAttributions(related)
        .build();
  }

  private static ErrorAttributionRestResponse.RelatedAttributionEntry toRelatedAttributionEntry(
      ErrorAttributionRelatedAttributionRow r) {
    return ErrorAttributionRestResponse.RelatedAttributionEntry.builder()
        .sourceSignal(r.sourceSignal())
        .rowKind(r.rowKind())
        .groupId(r.groupId())
        .title(r.title())
        .exceptionType(r.exceptionType())
        .url(r.url())
        .graphqlOperationName(r.graphqlOperationName())
        .graphqlOperationType(r.graphqlOperationType())
        .occurrences(r.occurrences())
        .nTreated(r.nTreated())
        .nControl(r.nControl())
        .nTreatedLow(r.nTreatedLow())
        .nControlLow(r.nControlLow())
        .p1(r.p1())
        .p2(r.p2())
        .rr(r.rr())
        .rrUndefined(r.rrUndefined())
        .rrUndefinedReason(r.rrUndefinedReason())
        .build();
  }
}

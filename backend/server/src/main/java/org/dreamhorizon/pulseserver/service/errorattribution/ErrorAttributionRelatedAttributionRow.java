package org.dreamhorizon.pulseserver.service.errorattribution;

/**
 * One row in the merged Track B “related attributions” list (response-only; not cached). {@link
 * #rowKind} is {@code issue} or {@code api}.
 */
public record ErrorAttributionRelatedAttributionRow(
    String sourceSignal,
    String rowKind,
    String groupId,
    String title,
    String exceptionType,
    String url,
    String graphqlOperationName,
    String graphqlOperationType,
    Long occurrences,
    Long nTreated,
    Long nControl,
    Long nTreatedLow,
    Long nControlLow,
    Double p1,
    Double p2,
    Double rr,
    Boolean rrUndefined,
    String rrUndefinedReason) {}

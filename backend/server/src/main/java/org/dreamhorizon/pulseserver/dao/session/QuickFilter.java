package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QuickFilter {

    FAILED_INTERACTIONS("sum(interactionErrors) > 0",
            "Failed Interactions", "Sessions with at least one failed interaction"),
    ERRORS_AND_CRASHES(
            "sum(networkErrors) > 0 OR sum(crashCount) > 0 OR sum(anrCount) > 0 OR sum(nonFatal) > 0",
            "Errors & Crashes", "Sessions with any error, crash, ANR, or non-fatal exception"),
    SLOW("sum(slowInteractionCount) > 0",
            "Slow", "Sessions with at least one slow interaction");

    private final String havingCondition;
    private final String displayName;
    private final String description;
}

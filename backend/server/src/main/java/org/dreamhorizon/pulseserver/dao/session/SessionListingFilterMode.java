package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How advanced filter conditions are combined in the session listing HAVING clause.
 */
@Getter
@RequiredArgsConstructor
public enum SessionListingFilterMode {

    MATCH_ALL("AND"),
    MATCH_ANY("OR");

    private final String sqlOperator;
}

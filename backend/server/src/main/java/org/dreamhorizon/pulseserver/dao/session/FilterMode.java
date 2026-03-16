package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FilterMode {

    MATCH_ALL("AND"),
    MATCH_ANY("OR");

    private final String sqlOperator;
}

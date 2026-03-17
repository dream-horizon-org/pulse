package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SortDirection {

    ASC("ASC", ">"),
    DESC("DESC", "<");

    private final String sql;

    /** Tuple comparison operator used in cursor-based pagination. */
    private final String cursorOp;
}

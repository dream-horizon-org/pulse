package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Sort order for session listing queries and cursor pagination.
 */
@Getter
@RequiredArgsConstructor
public enum SessionListingSortDirection {

    ASC("ASC", ">"),
    DESC("DESC", "<");

    private final String sql;

    /** Tuple comparison operator used in cursor-based pagination. */
    private final String cursorOp;
}

package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * UI grouping for session listing filter metadata (filters API).
 */
@Getter
@RequiredArgsConstructor
public enum SessionListingFilterCategory {

    SESSION("Session Properties"),
    USER("User Properties"),
    DEVICE("Device"),
    UI_INTERACTIONS("UI Interactions"),
    STABILITY("Stability / Errors"),
    GEOGRAPHY("Geography");

    private final String displayName;
}

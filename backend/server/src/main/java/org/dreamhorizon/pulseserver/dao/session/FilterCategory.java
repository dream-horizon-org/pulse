package org.dreamhorizon.pulseserver.dao.session;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FilterCategory {

    SESSION("Session Properties"),
    USER("User Properties"),
    DEVICE("Device"),
    UI_INTERACTIONS("UI Interactions"),
    STABILITY("Stability / Errors"),
    GEOGRAPHY("Geography");

    private final String displayName;
}

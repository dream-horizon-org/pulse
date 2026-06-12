package org.dreamhorizon.pulseserver.resources.session.models;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Issue types in severity order (ordinal = priority).
 * The backend iterates these in declaration order to build the ordered issues array.
 */
@Getter
@RequiredArgsConstructor
public enum IssueType {

    CRASH("Crashes"),
    ANR("ANRs"),
    NETWORK_ERROR("Network Errors"),
    NON_FATAL("Non-Fatals"),
    INTERACTION_ERROR("Interaction Errors"),
    SLOW_INTERACTION("Slow Interactions"),
    FROZEN_FRAME("Frozen Frames");

    private final String label;
}

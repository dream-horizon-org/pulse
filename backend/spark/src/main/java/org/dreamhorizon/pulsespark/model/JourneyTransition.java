package org.dreamhorizon.pulsespark.model;

public record JourneyTransition(
        long   journeyId,
        String projectId,
        String runTime,
        String direction,
        int    posFrom,    // -1 = ENTRY
        String eventFrom,  // "" = ENTRY
        int    posTo,
        String eventTo,
        long   userCount
) {}

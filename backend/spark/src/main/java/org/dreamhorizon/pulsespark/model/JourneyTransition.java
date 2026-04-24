package org.dreamhorizon.pulsespark.model;

public record JourneyTransition(
        long   journeyId,
        String projectId,
        String runTime,
        String direction,
        int    posFrom,
        String eventFrom,
        int    posTo,
        String eventTo,
        long   userCount
) {}

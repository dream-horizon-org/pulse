package org.dreamhorizon.pulseserver.dao.insightsnapshot.models;

import java.time.LocalDate;

public record DailySnapshot(LocalDate snapshotDate, String computedData) {}

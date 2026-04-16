package org.dreamhorizon.pulseserver.resources.performance.models.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionSessionStatsRow {
    private Long totalSessions;
    private Long successCount;
    private Long errorCount;
    private Long crash;
    private Long anr;
    private Double apdex;
    private Double p50Ms;
    private Double p95Ms;
    private Double p99Ms;
    private Long distinctErrorUsers;
}

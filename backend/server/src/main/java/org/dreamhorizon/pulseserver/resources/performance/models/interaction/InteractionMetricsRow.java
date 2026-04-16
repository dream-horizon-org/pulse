package org.dreamhorizon.pulseserver.resources.performance.models.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionMetricsRow {
    private Double apdex;
    private Double p50Ms;
    private Double p95Ms;
    private Double p99Ms;
    private Long successCount;
    private Long errorCount;
    private Long distinctErrorUsers;
    private Long frozenFrame;
    private Long analysedFrame;
    private Long unanalysedFrame;
    private Long crash;
    private Long anr;
    private Long net0;
    private Long net2xx;
    private Long net3xx;
    private Long net4xx;
    private Long net5xx;
    private Long netCount;
    private Double errorRatePct;
    private Double crashRatePct;
    private Double anrRatePct;
    private Double frozenFrameRatePct;
    private Double poorUserRatePct;
    private Double avgUserRatePct;
    private Double goodUserRatePct;
    private Double excellentUserRatePct;
    private Long userExcellent;
    private Long userGood;
    private Long userAverage;
    private Long userPoor;
}

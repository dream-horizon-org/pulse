package org.dreamhorizon.pulseserver.resources.performance.models.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionHealthRow {
    private String name;
    private Long totalCount;
    private Long successCount;
    private Long errorCount;
    private Double errorRatePct;
    private Double apdex;
    private Double p50Ms;
    private Long userExcellent;
    private Long userGood;
    private Long userAverage;
    private Long userPoor;
}

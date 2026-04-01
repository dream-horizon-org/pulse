package org.dreamhorizon.pulseserver.resources.funnel.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class FunnelsDto {
    private long id;
    private String projectId;
    private String name;
    private String description;
    private List<FunnelDefinitionStep> stepsJson;
    private long windowSeconds;
    private String mode;
    private int dateRangeDays;
    private List<FunnelAttributeFilter> filtersJson;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String status;
    private String funnelType;
    private Instant startTime;
    private Instant endTime;
}

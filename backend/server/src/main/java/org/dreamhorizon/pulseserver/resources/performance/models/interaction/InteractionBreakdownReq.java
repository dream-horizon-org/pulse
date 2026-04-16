package org.dreamhorizon.pulseserver.resources.performance.models.interaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionBreakdownReq {
    private String interactionName;
    private String dimension;
    private TimeRange timeRange;
    private Map<String, String> filters;
    private Integer limit;
    private List<InteractionOrderBy> orderBy;

    @JsonIgnore
    private String projectId;
}

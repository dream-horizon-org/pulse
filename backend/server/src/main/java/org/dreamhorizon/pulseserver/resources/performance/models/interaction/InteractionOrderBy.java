package org.dreamhorizon.pulseserver.resources.performance.models.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionOrderBy {
    private String field;
    private String direction;
}

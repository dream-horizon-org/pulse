package org.dreamhorizon.pulseserver.resources.performance.models.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionHealthRes {
    private List<InteractionHealthRow> interactions;
}

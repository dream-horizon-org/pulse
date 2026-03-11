package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FiltersRequest {

    @JsonProperty("quick")
    private List<String> quick;

    @JsonProperty("advanced")
    private AdvancedFilterGroup advanced;
}

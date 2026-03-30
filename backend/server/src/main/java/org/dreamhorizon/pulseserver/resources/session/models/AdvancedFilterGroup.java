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
public class AdvancedFilterGroup {

    @JsonProperty("op")
    private String op;

    @JsonProperty("children")
    private List<FilterConditionRequest> children;
}

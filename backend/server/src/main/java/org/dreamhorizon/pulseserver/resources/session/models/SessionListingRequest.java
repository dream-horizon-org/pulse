package org.dreamhorizon.pulseserver.resources.session.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionListingRequest {

    @NonNull
    @JsonProperty("timeRange")
    private TimeRangeRequest timeRange;

    @JsonProperty("page")
    private PageRequest page;

    @JsonProperty("filters")
    private FiltersRequest filters;

    @JsonProperty("query")
    private String query;

    @JsonProperty("sortBy")
    private String sortBy;

    @JsonProperty("sortDirection")
    private String sortDirection;
}

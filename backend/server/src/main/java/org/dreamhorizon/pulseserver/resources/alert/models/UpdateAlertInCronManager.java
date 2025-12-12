package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAlertInCronManager {
    @NotNull
    @JsonProperty("id")
    Integer id;

    @NotNull
    @JsonProperty("newInterval")
    Integer newInterval;

    @NotNull
    @JsonProperty("oldInterval")
    Integer oldInterval;

    @NotNull
    @JsonProperty("url")
    String url;
}

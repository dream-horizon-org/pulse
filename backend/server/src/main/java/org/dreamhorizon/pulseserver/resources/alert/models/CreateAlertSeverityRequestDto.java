package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAlertSeverityRequestDto {
    @NotNull
    @JsonProperty("name")
    Integer name;

    @NotNull
    @JsonProperty("description")
    String description;
}

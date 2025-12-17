package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertTagsResponseDto {
    @NotNull
    @JsonProperty("tag_id")
    Integer tagId;

    @NotNull
    @JsonProperty("name")
    String name;
}

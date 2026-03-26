package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCronDto {
  @NotNull
  @JsonProperty("id")
  Integer id;

  @NotNull
  @JsonProperty(value = "projectId")
  private String projectId;

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

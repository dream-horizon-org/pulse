package org.dreamhorizon.pulseserver.dto.response.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertTagsResponseDto {
  @NotNull
  @JsonProperty("tag_id")
  Integer tag_id;

  @NotNull
  @JsonProperty("name")
  String name;
}

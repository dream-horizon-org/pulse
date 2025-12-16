package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertFiltersResponseDto {
  @NotNull
  @JsonProperty("created_by")
  public List<String> createdBy;

  @NotNull
  @JsonProperty("updated_by")
  public List<String> updatedBy;

  @NotNull
  @JsonProperty("scope")
  public List<String> scope;

  @NotNull
  @JsonProperty("current_state")
  public List<AlertState> currentState;
}

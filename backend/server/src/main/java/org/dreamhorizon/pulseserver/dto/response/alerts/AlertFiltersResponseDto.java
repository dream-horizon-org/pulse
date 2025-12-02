package org.dreamhorizon.pulseserver.dto.response.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.enums.AlertState;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertFiltersResponseDto {
  @NotNull
  @JsonProperty("job_id")
  public List<String> job_id;

  @NotNull
  @JsonProperty("created_by")
  public List<String> created_by;

  @NotNull
  @JsonProperty("updated_by")
  public List<String> updated_by;

  @NotNull
  @JsonProperty("current_state")
  public List<AlertState> current_state;
}

package org.dreamhorizon.pulseserver.resources.alert.models;

import org.dreamhorizon.pulseserver.enums.AlertState;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.List;

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

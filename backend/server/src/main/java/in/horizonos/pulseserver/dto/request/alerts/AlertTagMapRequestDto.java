package in.horizonos.pulseserver.dto.request.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertTagMapRequestDto {
  @NotNull
  @JsonProperty("tag_id")
  Integer tag_id;
}

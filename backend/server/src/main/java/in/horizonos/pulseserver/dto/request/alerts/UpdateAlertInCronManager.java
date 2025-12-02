package in.horizonos.pulseserver.dto.request.alerts;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

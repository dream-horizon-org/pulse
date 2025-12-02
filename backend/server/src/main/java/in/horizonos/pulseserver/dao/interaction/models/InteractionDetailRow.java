package in.horizonos.pulseserver.dao.interaction.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.horizonos.pulseserver.service.interaction.models.Event;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InteractionDetailRow {
  private String description;
  private Integer uptimeLowerLimitInMs;
  private Integer uptimeMidLimitInMs;
  private Integer uptimeUpperLimitInMs;
  private Integer thresholdInMs;

  @Builder.Default
  private List<Event> events = List.of();
  private List<Event> globalBlacklistedEvents = List.of();
}
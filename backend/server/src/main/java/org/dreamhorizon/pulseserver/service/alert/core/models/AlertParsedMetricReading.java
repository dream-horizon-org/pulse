package org.dreamhorizon.pulseserver.service.alert.core.models;

import com.google.auto.value.AutoValue.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertParsedMetricReading {
  private Float reading;

  private Integer successInteractionCount;

  private Integer errorInteractionCount;

  private Integer totalInteractionCount;
}

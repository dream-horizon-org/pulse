package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatusRestDto {
  private JsonNode thresholdsNotified;
  private Instant createdAt;
}
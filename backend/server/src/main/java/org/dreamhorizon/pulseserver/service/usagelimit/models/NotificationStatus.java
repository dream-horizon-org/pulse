package org.dreamhorizon.pulseserver.service.usagelimit.models;

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
public class NotificationStatus {
  private JsonNode thresholdsNotified;
  private Instant createdAt;
}
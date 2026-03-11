package org.dreamhorizon.pulseserver.dao.sessiondetail.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionSpanRow {
  private String timestamp;
  @JsonProperty("event_type")
  private String eventType;
  private String description;
  @JsonProperty("duration_ns")
  private long durationNs;
  private String traceId;
  private String spanId;
}

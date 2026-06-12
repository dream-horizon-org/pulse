package org.dreamhorizon.pulseserver.service.alert.core.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertMetricReading {
  @JsonProperty("reading")
  private Float reading;

  @JsonProperty("useCaseId")
  private String useCaseId;

  @JsonProperty("successInteractionCount")
  private Integer successInteractionCount;

  @JsonProperty("errorInteractionCount")
  private Integer errorInteractionCount;

  @JsonProperty("totalInteractionCount")
  Integer totalInteractionCount;

  @JsonProperty("timestamp")
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  private LocalDateTime timestamp;
}

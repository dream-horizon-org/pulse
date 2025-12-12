package org.dreamhorizon.pulseserver.dto.v2.request.incident;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IncidentReportRequestDto {
  @JsonProperty("startTime")
  private String startTime;

  @JsonProperty("endTime")
  private String endTime;
}

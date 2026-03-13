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
public class SessionExceptionRow {
  private String timestamp;
  @JsonProperty("pulse_type")
  private String pulseType;
  private String title;
  @JsonProperty("exception_stack_trace")
  private String exceptionStackTrace;
  private String traceId;
  private String spanId;
}

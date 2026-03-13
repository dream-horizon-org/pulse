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
public class NetworkRequestRow {
  private String timestamp;
  @JsonProperty("duration_ns")
  private long durationNs;
  @JsonProperty("http_method")
  private String httpMethod;
  @JsonProperty("http_url")
  private String httpUrl;
  @JsonProperty("http_status_code")
  private String httpStatusCode;
  @JsonProperty("http_target")
  private String httpTarget;
  private String description;
  private String traceId;
  private String spanId;
}

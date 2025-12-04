package org.dreamhorizon.pulseserver.dto.v1.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetJobStatusResponseDto {

  @JsonProperty("message")
  private String message;

  @JsonProperty("status")
  private String status;
}

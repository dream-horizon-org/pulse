package org.dreamhorizon.pulseserver.dto.v1.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JobCreateResponseDto {
  @JsonProperty("status")
  private Boolean status;

  @JsonProperty("jobId")
  private Long jobId;
}

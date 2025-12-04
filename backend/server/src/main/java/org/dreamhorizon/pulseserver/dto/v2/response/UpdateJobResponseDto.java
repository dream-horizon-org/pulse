package org.dreamhorizon.pulseserver.dto.v2.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateJobResponseDto {
  @JsonProperty("status")
  private Integer status;

  @JsonProperty("jobId")
  private Long jobId;
}

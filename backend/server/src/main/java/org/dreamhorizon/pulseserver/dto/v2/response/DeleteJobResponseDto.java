package org.dreamhorizon.pulseserver.dto.v2.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeleteJobResponseDto {
  @JsonProperty("status")
  private Integer status;

  @JsonProperty("message")
  private String message;
}

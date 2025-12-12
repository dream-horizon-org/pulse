package org.dreamhorizon.pulseserver.dto.v2.request.query;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetQueryJobResultRequest {
  @JsonProperty("pageToken")
  private String pageToken;

  @JsonProperty("jobId")
  @NotBlank(message = "jobId cannot be blank")
  private String jobId;

}


package org.dreamhorizon.pulseserver.dto.v2.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateJobRawResponseDto {
  @JsonProperty("job")
  private CreateJobRawResponseDto.JobData job;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class JobData {
    @JsonProperty("id")
    private String id;
  }
}

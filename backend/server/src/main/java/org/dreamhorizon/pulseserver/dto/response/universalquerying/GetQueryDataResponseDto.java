package org.dreamhorizon.pulseserver.dto.response.universalquerying;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GetQueryDataResponseDto<T> {

  @JsonProperty("data")
  public T data;

  @JsonProperty("jobComplete")
  public boolean jobComplete;

  @JsonProperty("jobReference")
  public JobReference jobReference;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class JobReference {
    @JsonProperty("jobId")
    public String jobId;
  }
}

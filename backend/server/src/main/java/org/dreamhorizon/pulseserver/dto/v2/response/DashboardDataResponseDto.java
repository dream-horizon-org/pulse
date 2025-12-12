package org.dreamhorizon.pulseserver.dto.v2.response;

import org.dreamhorizon.pulseserver.dto.v2.response.universalquerying.GetQueryDataResponseDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardDataResponseDto {
  @JsonProperty("filteredResults")
  private List<?> filteredResults;

  @JsonProperty("apdexResults")
  private List<?> apdexResults;

  @JsonProperty("interactionTimeResults")
  private List<?> interactionTimeResults;

  @JsonProperty("userCategorizationResults")
  private List<?> userCategorizationResults;

  @JsonProperty("errorInteractionResults")
  private List<?> errorInteractionResults;

  @JsonProperty("jobComplete")
  private Boolean jobComplete;

  @JsonProperty("jobReference")
  private JobReference jobReference;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class JobReference {
    @JsonProperty("jobId")
    private String jobId;

    public static JobReference from(GetQueryDataResponseDto.JobReference jobReference) {
      if (jobReference == null) {
        return null;
      }

      return JobReference.builder().jobId(jobReference.jobId).build();
    }
  }
}

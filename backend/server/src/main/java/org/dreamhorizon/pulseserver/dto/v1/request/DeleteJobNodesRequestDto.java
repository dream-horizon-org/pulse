package org.dreamhorizon.pulseserver.dto.v1.request;

import org.dreamhorizon.pulseserver.error.ServiceError;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteJobNodesRequestDto {
  @JsonProperty("jobId")
  private Long jobId;

  @JsonProperty("nodeIds")
  private List<Long> nodeIds;

  public void validate() {
    if (jobId == null || jobId <= 0) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getException();
    }

    if (nodeIds == null || nodeIds.isEmpty() || nodeIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getException();
    }
  }
}

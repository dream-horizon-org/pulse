package org.dreamhorizon.pulseserver.dto.v1.request;

import org.dreamhorizon.pulseserver.error.ServiceError;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import jakarta.ws.rs.QueryParam;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetJobDetailsRequestDto {
  @QueryParam("jobId")
  public Long jobId;

  public void validate() {
    if (jobId != null && (!StringUtils.isNumeric(jobId.toString()) || jobId < 0)) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getException();
    }
  }
}

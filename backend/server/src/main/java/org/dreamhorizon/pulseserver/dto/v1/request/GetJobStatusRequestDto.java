package org.dreamhorizon.pulseserver.dto.v1.request;

import org.dreamhorizon.pulseserver.error.ServiceError;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetJobStatusRequestDto {
  @QueryParam("jobId")
  public Long jobId;


  void validateParams() {
    if (jobId != null && (!StringUtils.isNumeric(jobId.toString()) || jobId < 0)) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getException();
    }
  }

  public void validate() {
    validateParams();
  }
}

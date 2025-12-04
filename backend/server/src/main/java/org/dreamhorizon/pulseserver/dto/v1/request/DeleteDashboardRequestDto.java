package org.dreamhorizon.pulseserver.dto.v1.request;

import org.dreamhorizon.pulseserver.error.ServiceError;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteDashboardRequestDto {
  @JsonProperty(value = "dashboardId")
  public String dashboardId;

  public void validate() {
    if (dashboardId.isEmpty()) {
      throw ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getException();
    }
  }
}

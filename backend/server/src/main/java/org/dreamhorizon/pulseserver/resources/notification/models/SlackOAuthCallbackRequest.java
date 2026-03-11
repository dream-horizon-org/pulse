package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.ws.rs.QueryParam;
import lombok.Data;

@Data
public class SlackOAuthCallbackRequest {

  @QueryParam("code")
  private String code;

  @QueryParam("state")
  private String projectId;

  @QueryParam("error")
  private String error;

  public boolean hasError() {
    return error != null && !error.isBlank();
  }

  public boolean isValid() {
    if (hasError()) {
      return true;
    }
    return code != null && !code.isBlank()
        && projectId != null && !projectId.isBlank();
  }

  public String getValidationError() {
    if (hasError()) {
      return null;
    }
    if (code == null || code.isBlank()) {
      return "Authorization code is required";
    }
    if (projectId == null || projectId.isBlank()) {
      return "Project ID (state) is required";
    }
    return null;
  }
}

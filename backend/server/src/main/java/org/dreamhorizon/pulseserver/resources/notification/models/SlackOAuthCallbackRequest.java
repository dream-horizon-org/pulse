package org.dreamhorizon.pulseserver.resources.notification.models;

import jakarta.ws.rs.QueryParam;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.Data;

@Data
public class SlackOAuthCallbackRequest {

  @QueryParam("code")
  private String code;

  @QueryParam("state")
  private String state;

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
        && getProjectId() != null && !getProjectId().isBlank();
  }

  public String getValidationError() {
    if (hasError()) {
      return null;
    }
    if (code == null || code.isBlank()) {
      return "Authorization code is required";
    }
    if (getProjectId() == null || getProjectId().isBlank()) {
      return "Project ID (state) is required";
    }
    return null;
  }

  public String getProjectId() {
    if (state == null || state.isBlank()) {
      return null;
    }
    int separator = state.indexOf("::");
    if (separator < 0) {
      return state;
    }
    return state.substring(0, separator);
  }

  public void setProjectId(String projectId) {
    this.state = projectId;
  }

  public String getReturnPath() {
    if (state == null || state.isBlank()) {
      return null;
    }
    int separator = state.indexOf("::");
    if (separator < 0 || separator + 2 >= state.length()) {
      return null;
    }
    String encodedPath = state.substring(separator + 2);
    try {
      byte[] decoded = Base64.getUrlDecoder().decode(encodedPath);
      String path = new String(decoded, StandardCharsets.UTF_8);
      if (!path.startsWith("/") || path.startsWith("//")) {
        return null;
      }
      return path;
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}

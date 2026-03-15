package org.dreamhorizon.pulseserver.resources.eventdefinition.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestBulkUploadResponse {
  private int created;
  private int updated;
  private int skipped;
  private List<RowError> errors;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class RowError {
    private int line;
    private String eventName;
    private String message;
  }
}

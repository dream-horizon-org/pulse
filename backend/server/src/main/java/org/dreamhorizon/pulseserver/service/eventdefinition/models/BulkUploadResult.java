package org.dreamhorizon.pulseserver.service.eventdefinition.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BulkUploadResult {
  private int created;
  private int updated;
  private int skipped;
  private List<RowError> errors;

  @Getter
  @Builder
  @AllArgsConstructor
  public static class RowError {
    private int line;
    private String eventName;
    private String message;
  }
}

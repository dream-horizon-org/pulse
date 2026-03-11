package org.dreamhorizon.pulseserver.resources.v1.tnc.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TncHistoryResponse {
  private List<TncHistoryEntry> history;
  private int totalCount;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TncHistoryEntry {
    private Long versionId;
    private String acceptedBy;
    private String acceptedAt;
  }
}

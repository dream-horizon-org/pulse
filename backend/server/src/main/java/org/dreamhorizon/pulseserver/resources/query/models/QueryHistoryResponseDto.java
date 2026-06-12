package org.dreamhorizon.pulseserver.resources.query.models;

import io.vertx.core.json.JsonArray;
import java.sql.Timestamp;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryHistoryResponseDto {
  private List<QueryHistoryItem> queries;
  private Integer total;
  private Integer limit;
  private Integer offset;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class QueryHistoryItem {
    private String jobId;
    private String queryString;
    private String queryExecutionId;
    private String status;
    private String resultLocation;
    private String errorMessage;
    private Long dataScannedInBytes;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp completedAt;
  }
}


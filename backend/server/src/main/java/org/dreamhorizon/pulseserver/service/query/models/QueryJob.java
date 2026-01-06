package org.dreamhorizon.pulseserver.service.query.models;

import io.vertx.core.json.JsonArray;
import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryJob {
  private String jobId;
  private String queryString;
  private String queryExecutionId;
  private QueryJobStatus status;
  private String resultLocation;
  private String errorMessage;
  private JsonArray resultData;
  private String nextToken;
  private Long dataScannedInBytes;
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private Timestamp completedAt;
}


package org.dreamhorizon.pulseserver.resources.query.models;

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
public class SubmitQueryResponseDto {
  private String jobId;
  private String status;
  private String message;
  private String queryExecutionId;
  private String resultLocation;
  private JsonArray resultData;
  private String nextToken;
  private Long dataScannedInBytes;
  private Timestamp createdAt;
  private Timestamp completedAt;
}


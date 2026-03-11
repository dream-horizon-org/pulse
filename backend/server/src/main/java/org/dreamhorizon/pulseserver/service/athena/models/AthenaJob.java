package org.dreamhorizon.pulseserver.service.athena.models;

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
public class AthenaJob {
  private String jobId;
  private String tenantId; // Parent tenant for organizational hierarchy
  private String projectId; // Project where query was executed (data isolation)
  private String queryString;
  private String userEmail;
  private String queryExecutionId;
  private AthenaJobStatus status;
  private String resultLocation;
  private String errorMessage;
  private JsonArray resultData;
  private String nextToken; // For pagination
  private Long dataScannedInBytes; // Data scanned by the query (in bytes)
  private Long executionTimeMillis; // Total execution time in milliseconds
  private Long engineExecutionTimeMillis; // Engine execution time in milliseconds
  private Long queryQueueTimeMillis; // Query queue time in milliseconds
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private Timestamp completedAt;
}




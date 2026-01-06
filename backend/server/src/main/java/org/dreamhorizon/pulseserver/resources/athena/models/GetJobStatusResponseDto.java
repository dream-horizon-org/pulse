package org.dreamhorizon.pulseserver.resources.athena.models;

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
public class GetJobStatusResponseDto {
  private String jobId;
  private String queryString;
  private String queryExecutionId;
  private String status;
  private String resultLocation;
  private String errorMessage;
  private JsonArray resultData; // Paginated results
  private String nextToken; // For pagination
  private Long dataScannedInBytes; // Data scanned by the query (in bytes)
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private Timestamp completedAt;
}




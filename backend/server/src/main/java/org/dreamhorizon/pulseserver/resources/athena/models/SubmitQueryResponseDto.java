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
public class SubmitQueryResponseDto {
  private String jobId;
  private String status;
  private String message;
  private String queryExecutionId;
  private String resultLocation;
  private JsonArray resultData; // Included if query completed within 3 seconds
  private String nextToken; // For pagination if results are included
  private Long dataScannedInBytes; // Data scanned by the query (in bytes)
  private Timestamp createdAt;
  private Timestamp completedAt;
}




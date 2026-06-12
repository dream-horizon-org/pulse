package org.dreamhorizon.pulseserver.client.query.models;

import java.sql.Timestamp;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryExecutionInfo {
  private String queryExecutionId;
  private QueryStatus status;
  private String stateChangeReason;
  private String resultLocation;
  private Long dataScannedInBytes;
  private Long executionTimeMillis;
  private Long engineExecutionTimeMillis;
  private Long queryQueueTimeMillis;
  private Timestamp submissionDateTime;
  private Timestamp completionDateTime;
}


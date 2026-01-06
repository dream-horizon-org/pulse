package org.dreamhorizon.pulseserver.client.query.models;

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
}


package org.dreamhorizon.pulseserver.resources.query.models;

import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelQueryResponseDto {
  private String jobId;
  private String status;
  private String message;
  private String queryExecutionId;
  private Long dataScannedInBytes;
  private Timestamp updatedAt;
}



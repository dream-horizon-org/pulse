package org.dreamhorizon.pulseserver.resources.v1.projects.clickhouse.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditHistoryResponse {
  private List<AuditLogResponse> logs;
  private Integer count;
}

package org.dreamhorizon.pulseserver.service.query.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMetadata {
  private String tableName;
  private String tableSchema;
  private String tableType;
  private List<ColumnMetadata> columns;
}


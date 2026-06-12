package org.dreamhorizon.pulseserver.service.query.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetadata {
  private String columnName;
  private String dataType;
  private Integer ordinalPosition;
  private String isNullable;
}


package org.dreamhorizon.pulseserver.resources.query.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetadataResponseDto {
  private String columnName;
  private String dataType;
  private Integer ordinalPosition;
  private String isNullable;
}


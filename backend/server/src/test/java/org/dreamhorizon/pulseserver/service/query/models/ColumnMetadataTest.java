package org.dreamhorizon.pulseserver.service.query.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ColumnMetadataTest {

  @Test
  void shouldCreateWithNoArgsConstructor() {
    ColumnMetadata column = new ColumnMetadata();
    
    assertThat(column).isNotNull();
    assertThat(column.getColumnName()).isNull();
    assertThat(column.getDataType()).isNull();
    assertThat(column.getOrdinalPosition()).isNull();
    assertThat(column.getIsNullable()).isNull();
  }

  @Test
  void shouldCreateWithAllArgsConstructor() {
    ColumnMetadata column = new ColumnMetadata("id", "varchar", 1, "NO");
    
    assertThat(column.getColumnName()).isEqualTo("id");
    assertThat(column.getDataType()).isEqualTo("varchar");
    assertThat(column.getOrdinalPosition()).isEqualTo(1);
    assertThat(column.getIsNullable()).isEqualTo("NO");
  }

  @Test
  void shouldCreateWithBuilder() {
    ColumnMetadata column = ColumnMetadata.builder()
        .columnName("name")
        .dataType("varchar")
        .ordinalPosition(2)
        .isNullable("YES")
        .build();
    
    assertThat(column.getColumnName()).isEqualTo("name");
    assertThat(column.getDataType()).isEqualTo("varchar");
    assertThat(column.getOrdinalPosition()).isEqualTo(2);
    assertThat(column.getIsNullable()).isEqualTo("YES");
  }

  @Test
  void shouldSetAndGetValues() {
    ColumnMetadata column = new ColumnMetadata();
    column.setColumnName("email");
    column.setDataType("varchar");
    column.setOrdinalPosition(3);
    column.setIsNullable("NO");
    
    assertThat(column.getColumnName()).isEqualTo("email");
    assertThat(column.getDataType()).isEqualTo("varchar");
    assertThat(column.getOrdinalPosition()).isEqualTo(3);
    assertThat(column.getIsNullable()).isEqualTo("NO");
  }
}


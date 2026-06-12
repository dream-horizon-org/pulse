package org.dreamhorizon.pulseserver.service.query.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class TableMetadataTest {

  @Test
  void shouldCreateWithNoArgsConstructor() {
    TableMetadata table = new TableMetadata();
    
    assertThat(table).isNotNull();
    assertThat(table.getTableName()).isNull();
    assertThat(table.getTableSchema()).isNull();
    assertThat(table.getTableType()).isNull();
    assertThat(table.getColumns()).isNull();
  }

  @Test
  void shouldCreateWithAllArgsConstructor() {
    ColumnMetadata column = ColumnMetadata.builder()
        .columnName("id")
        .dataType("varchar")
        .build();
    
    TableMetadata table = new TableMetadata("table1", "schema1", "BASE TABLE", Arrays.asList(column));
    
    assertThat(table.getTableName()).isEqualTo("table1");
    assertThat(table.getTableSchema()).isEqualTo("schema1");
    assertThat(table.getTableType()).isEqualTo("BASE TABLE");
    assertThat(table.getColumns()).hasSize(1);
  }

  @Test
  void shouldCreateWithBuilder() {
    TableMetadata table = TableMetadata.builder()
        .tableName("table1")
        .tableSchema("schema1")
        .tableType("BASE TABLE")
        .columns(new ArrayList<>())
        .build();
    
    assertThat(table.getTableName()).isEqualTo("table1");
    assertThat(table.getTableSchema()).isEqualTo("schema1");
    assertThat(table.getTableType()).isEqualTo("BASE TABLE");
    assertThat(table.getColumns()).isEmpty();
  }

  @Test
  void shouldSetAndGetValues() {
    TableMetadata table = new TableMetadata();
    table.setTableName("table1");
    table.setTableSchema("schema1");
    table.setTableType("VIEW");
    table.setColumns(Collections.emptyList());
    
    assertThat(table.getTableName()).isEqualTo("table1");
    assertThat(table.getTableSchema()).isEqualTo("schema1");
    assertThat(table.getTableType()).isEqualTo("VIEW");
    assertThat(table.getColumns()).isEmpty();
  }
}


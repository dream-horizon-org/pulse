package org.dreamhorizon.pulseserver.client.athena;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.ResultSetMetadata;
import software.amazon.awssdk.services.athena.model.Row;

public class AthenaResultConverterTest {

  private AthenaResultConverter converter;

  @BeforeEach
  void setUp() {
    converter = new AthenaResultConverter();
  }

  @Nested
  class TestConvertToJsonArray {

    @Test
    void shouldReturnEmptyArrayWhenResultSetIsNull() {
      JsonArray result = converter.convertToJsonArray(null);
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyArrayWhenMetadataIsNull() {
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata((ResultSetMetadata) null)
          .build();
      JsonArray result = converter.convertToJsonArray(resultSet);
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyArrayWhenColumnInfoIsNull() {
      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo((java.util.Collection<ColumnInfo>) null)
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .build();
      JsonArray result = converter.convertToJsonArray(resultSet);
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyArrayWhenRowsIsNull() {
      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows((java.util.Collection<Row>) null)
          .build();
      JsonArray result = converter.convertToJsonArray(resultSet);
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyArrayWhenRowsIsEmpty() {
      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Collections.emptyList())
          .build();
      JsonArray result = converter.convertToJsonArray(resultSet);
      assertThat(result).isNotNull();
      assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipFirstRow() {
      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();

      Row headerRow = Row.builder()
          .data(Arrays.asList(Datum.builder().varCharValue("col1").build()))
          .build();
      Row dataRow = Row.builder()
          .data(Arrays.asList(Datum.builder().varCharValue("value1").build()))
          .build();

      List<Row> rows = Arrays.asList(headerRow, dataRow);
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(rows)
          .build();
      JsonArray result = converter.convertToJsonArray(resultSet);
      assertThat(result).hasSize(1);
      JsonObject firstRow = result.getJsonObject(0);
      assertThat(firstRow.getString("col1")).isEqualTo("value1");
    }

    @Test
    void shouldConvertMultipleRows() {
      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(Arrays.asList(
              ColumnInfo.builder().name("col1").build(),
              ColumnInfo.builder().name("col2").build()
          ))
          .build();

      Row headerRow = Row.builder()
          .data(Arrays.asList(
              Datum.builder().varCharValue("col1").build(),
              Datum.builder().varCharValue("col2").build()
          ))
          .build();

      Row dataRow1 = Row.builder()
          .data(Arrays.asList(
              Datum.builder().varCharValue("value1").build(),
              Datum.builder().varCharValue("value2").build()
          ))
          .build();

      Row dataRow2 = Row.builder()
          .data(Arrays.asList(
              Datum.builder().varCharValue("value3").build(),
              Datum.builder().varCharValue("value4").build()
          ))
          .build();

      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Arrays.asList(headerRow, dataRow1, dataRow2))
          .build();

      JsonArray result = converter.convertToJsonArray(resultSet);

      assertThat(result).hasSize(2);

      JsonObject row0 = result.getJsonObject(0);
      JsonObject row1 = result.getJsonObject(1);

      assertThat(row0).isNotNull();
      assertThat(row1).isNotNull();

      assertThat(row0.containsKey("col1")).isTrue();
      assertThat(row0.containsKey("col2")).isTrue();
      assertThat(row1.containsKey("col1")).isTrue();
      assertThat(row1.containsKey("col2")).isTrue();

      // optional but useful: validate values too
      assertThat(row0.getString("col1")).isEqualTo("value1");
      assertThat(row0.getString("col2")).isEqualTo("value2");
      assertThat(row1.getString("col1")).isEqualTo("value3");
      assertThat(row1.getString("col2")).isEqualTo("value4");
    }

    @Test
    void shouldHandleNullDataInRow() {
      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();

      Row headerRow = Row.builder()
          .data(Arrays.asList(Datum.builder().varCharValue("col1").build()))
          .build();
      Row dataRow = Row.builder()
          .data((java.util.Collection<Datum>) null)
          .build();

      List<Row> rows = Arrays.asList(headerRow, dataRow);
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(rows)
          .build();
      JsonArray result = converter.convertToJsonArray(resultSet);
      assertThat(result).hasSize(1);
      JsonObject row = result.getJsonObject(0);
      assertThat(row).isEmpty();
    }

    @Test
    void shouldHandleNullDatumInRow() {
      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(Arrays.asList(
              ColumnInfo.builder().name("col1").build(),
              ColumnInfo.builder().name("col2").build()
          ))
          .build();

      Row headerRow = Row.builder()
          .data(Arrays.asList(
              Datum.builder().varCharValue("col1").build(),
              Datum.builder().varCharValue("col2").build()
          ))
          .build();

      Row dataRow = Row.builder()
          .data(Arrays.asList(
              Datum.builder().varCharValue("value1").build(),
              null
          ))
          .build();

      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Arrays.asList(headerRow, dataRow))
          .build();

      JsonArray result = converter.convertToJsonArray(resultSet);

      assertThat(result).hasSize(1);
      JsonObject row = result.getJsonObject(0);
      assertThat(row).isNotNull();

      assertThat(row.containsKey("col1")).isTrue();
      assertThat(row.containsKey("col2")).isTrue();

      assertThat(row.getString("col1")).isEqualTo("value1");
      assertThat(row.getValue("col2")).isNull();
    }

    @Test
    void shouldHandleNullVarCharValue() {
      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();

      Row headerRow = Row.builder()
          .data(Arrays.asList(Datum.builder().varCharValue("col1").build()))
          .build();
      Row dataRow = Row.builder()
          .data(Arrays.asList(Datum.builder().varCharValue(null).build()))
          .build();

      List<Row> rows = Arrays.asList(headerRow, dataRow);
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(rows)
          .build();
      JsonArray result = converter.convertToJsonArray(resultSet);
      assertThat(result).hasSize(1);
      JsonObject row = result.getJsonObject(0);
      assertThat(row.getValue("col1")).isNull();
    }
  }
}

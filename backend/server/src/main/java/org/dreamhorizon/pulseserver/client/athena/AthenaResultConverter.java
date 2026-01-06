package org.dreamhorizon.pulseserver.client.athena;

import com.google.inject.Inject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.Row;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AthenaResultConverter {
  
  public JsonArray convertToJsonArray(ResultSet resultSet) {
    JsonArray result = new JsonArray();

    if (resultSet.resultSetMetadata() == null || resultSet.resultSetMetadata().columnInfo() == null) {
      return result;
    }

    List<String> columnNames = extractColumnNames(resultSet);

    if (resultSet.rows() != null && !resultSet.rows().isEmpty()) {
      boolean isFirstRow = true;
      for (Row row : resultSet.rows()) {
        if (isFirstRow) {
          isFirstRow = false;
          continue;
        }
        result.add(convertRowToJsonObject(row, columnNames));
      }
    }

    return result;
  }

  private List<String> extractColumnNames(ResultSet resultSet) {
    List<String> columnNames = new ArrayList<>();
    resultSet.resultSetMetadata().columnInfo().forEach(column -> columnNames.add(column.name()));
    return columnNames;
  }

  private JsonObject convertRowToJsonObject(Row row, List<String> columnNames) {
    JsonObject rowObject = new JsonObject();
    int maxColumns = Math.min(columnNames.size(), row.data().size());
    
    for (int i = 0; i < maxColumns; i++) {
      String columnName = columnNames.get(i);
      String value = row.data().get(i).varCharValue();
      rowObject.put(columnName, value);
    }
    
    return rowObject;
  }
}


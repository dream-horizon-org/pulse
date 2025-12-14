package org.dreamhorizon.pulseserver.util;

import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertFiltersResponseDto;

public class AlertMapper {


  public static AlertFiltersResponseDto mapRowSetToAlertFilters(@NotNull RowSet<Row> rowSet) {
    List<String> jobIds = new ArrayList<>();
    List<String> createdBy = new ArrayList<>();
    List<String> updatedBy = new ArrayList<>();
    List<AlertState> currentStates = new ArrayList<>();

    for (Row row : rowSet) {
      String createdByValue = row.getString("created_by");
      String updatedByValue = row.getString("updated_by");

      if (createdByValue != null && !createdBy.contains(createdByValue) && !createdByValue.isEmpty()) {
        createdBy.add(createdByValue);
      }
      if (updatedByValue != null && !updatedBy.contains(updatedByValue) && !updatedByValue.isEmpty()) {
        updatedBy.add(updatedByValue);
      }
      String currentStateValue = row.getString("current_state");
      if (currentStateValue != null && !currentStateValue.isEmpty() && !currentStates.contains(AlertState.valueOf(currentStateValue))) {
        currentStates.add(AlertState.valueOf(currentStateValue));
      }
    }

    return AlertFiltersResponseDto.builder()
        .createdBy(createdBy)
        .updatedBy(updatedBy)
        .currentState(currentStates)
        .build();
  }
}
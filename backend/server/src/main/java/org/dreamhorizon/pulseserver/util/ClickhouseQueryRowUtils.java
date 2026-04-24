package org.dreamhorizon.pulseserver.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;

/**
 * Maps ClickHouse universal-query responses (root-cause / drill-down shape) to row maps.
 * Shared by root-cause and error-attribution drill-down services.
 */
@UtilityClass
public class ClickhouseQueryRowUtils {

  /**
   * Converts response rows to column-name → value maps.
   *
   * @param response ClickHouse job wrapper; must be complete with non-null data for rows
   * @return empty list if job incomplete or data null; else one map per row, null-padding
   *     trailing fields when row is shorter than schema
   */
  public static List<Map<String, Object>> rowsToMaps(
      final GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    if (!response.isJobComplete() || response.getData() == null) {
      return List.of();
    }
    GetRawUserEventsResponseDto data = response.getData();
    List<String> names =
        data.getSchema().getFields().stream()
            .map(GetRawUserEventsResponseDto.Field::getName)
            .toList();
    List<Map<String, Object>> out = new ArrayList<>();
    for (GetRawUserEventsResponseDto.Row row : data.getRows()) {
      Map<String, Object> m = new LinkedHashMap<>();
      for (int i = 0; i < names.size(); i++) {
        Object v =
            i < row.getRowFields().size() ? row.getRowFields().get(i).getValue() : null;
        m.put(names.get(i), v);
      }
      out.add(m);
    }
    return out;
  }
}

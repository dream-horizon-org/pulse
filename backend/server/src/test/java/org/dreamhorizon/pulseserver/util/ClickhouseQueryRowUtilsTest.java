package org.dreamhorizon.pulseserver.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.junit.jupiter.api.Test;

class ClickhouseQueryRowUtilsTest {

  @Test
  void rowsToMaps_returnsEmptyWhenJobIncomplete() {
    GetQueryDataResponseDto<GetRawUserEventsResponseDto> response =
        GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
            .jobComplete(false)
            .data(GetRawUserEventsResponseDto.builder().build())
            .build();

    assertThat(ClickhouseQueryRowUtils.rowsToMaps(response)).isEmpty();
  }

  @Test
  void rowsToMaps_returnsEmptyWhenDataNull() {
    GetQueryDataResponseDto<GetRawUserEventsResponseDto> response =
        GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
            .jobComplete(true)
            .data(null)
            .build();

    assertThat(ClickhouseQueryRowUtils.rowsToMaps(response)).isEmpty();
  }

  @Test
  void rowsToMaps_padsMissingTrailingFieldsWithNull() {
    GetRawUserEventsResponseDto.Field fg = new GetRawUserEventsResponseDto.Field();
    fg.setName("group_id");
    GetRawUserEventsResponseDto.Field ft = new GetRawUserEventsResponseDto.Field();
    ft.setName("title");
    GetRawUserEventsResponseDto.Field f1 = new GetRawUserEventsResponseDto.Field();
    f1.setName("n_treated");
    GetRawUserEventsResponseDto.Row row = new GetRawUserEventsResponseDto.Row();
    GetRawUserEventsResponseDto.RowField r0 = new GetRawUserEventsResponseDto.RowField();
    r0.setValue("gid");
    GetRawUserEventsResponseDto.RowField r1 = new GetRawUserEventsResponseDto.RowField();
    r1.setValue("Row title");
    GetRawUserEventsResponseDto.RowField r2 = new GetRawUserEventsResponseDto.RowField();
    r2.setValue(10L);
    row.setRowFields(List.of(r0, r1, r2));

    GetRawUserEventsResponseDto data =
        GetRawUserEventsResponseDto.builder()
            .schema(
                new GetRawUserEventsResponseDto.Schema(List.of(fg, ft, f1)))
            .rows(List.of(row))
            .build();
    GetQueryDataResponseDto<GetRawUserEventsResponseDto> response =
        GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
            .jobComplete(true)
            .data(data)
            .build();

    List<Map<String, Object>> rows = ClickhouseQueryRowUtils.rowsToMaps(response);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("group_id", "gid")
        .containsEntry("title", "Row title")
        .containsEntry("n_treated", 10L);
  }
}

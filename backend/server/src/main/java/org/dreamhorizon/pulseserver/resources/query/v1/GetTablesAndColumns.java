package org.dreamhorizon.pulseserver.resources.query.v1;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.query.models.ColumnMetadataResponseDto;
import org.dreamhorizon.pulseserver.resources.query.models.TableMetadataResponseDto;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.ColumnMetadata;
import org.dreamhorizon.pulseserver.service.query.models.TableMetadata;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/query")
public class GetTablesAndColumns {
  private final QueryService queryService;

  @GET
  @Path("/tables")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<List<TableMetadataResponseDto>>> getTablesAndColumns() {
    return queryService.getTablesAndColumns()
        .map(tables -> tables.stream()
            .map(this::mapToResponseDto)
            .collect(Collectors.toList()))
        .to(RestResponse.jaxrsRestHandler());
  }

  private TableMetadataResponseDto mapToResponseDto(TableMetadata table) {
    List<ColumnMetadataResponseDto> columns = table.getColumns().stream()       
        .map(this::mapColumnToResponseDto)
        .collect(Collectors.toList());

    return TableMetadataResponseDto.builder()
        .tableName(table.getTableName())
        .tableSchema(table.getTableSchema())
        .tableType(table.getTableType())
        .columns(columns)
        .build();
  }

  private ColumnMetadataResponseDto mapColumnToResponseDto(ColumnMetadata column) {
    return ColumnMetadataResponseDto.builder()
        .columnName(column.getColumnName())
        .dataType(column.getDataType())
        .ordinalPosition(column.getOrdinalPosition())
        .isNullable(column.getIsNullable())
        .build();
  }
}


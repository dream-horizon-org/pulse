package org.dreamhorizon.pulseserver.resources.query.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.resources.query.models.ColumnMetadataResponseDto;
import org.dreamhorizon.pulseserver.resources.query.models.TableMetadataResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.ColumnMetadata;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.dreamhorizon.pulseserver.service.query.models.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class GetTablesAndColumnsTest {

  @Mock
  QueryService queryService;

  GetTablesAndColumns getTablesAndColumns;

  @BeforeEach
  void setUp() {
    getTablesAndColumns = new GetTablesAndColumns(queryService);
  }

  @Test
  void shouldGetTablesAndColumnsSuccessfully(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      List<ColumnMetadata> columns1 = Arrays.asList(
          ColumnMetadata.builder()
              .columnName("id")
              .dataType("varchar")
              .ordinalPosition(1)
              .isNullable("NO")
              .build(),
          ColumnMetadata.builder()
              .columnName("name")
              .dataType("varchar")
              .ordinalPosition(2)
              .isNullable("YES")
              .build()
      );

      List<ColumnMetadata> columns2 = Collections.emptyList();

      List<TableMetadata> tables = Arrays.asList(
          TableMetadata.builder()
              .tableName("table1")
              .tableSchema("test_db")
              .tableType("BASE TABLE")
              .columns(columns1)
              .build(),
          TableMetadata.builder()
              .tableName("table2")
              .tableSchema("test_db")
              .tableType("BASE TABLE")
              .columns(columns2)
              .build()
      );

      when(queryService.getTablesAndColumns()).thenReturn(Single.just(tables));

      CompletionStage<Response<List<TableMetadataResponseDto>>> result = getTablesAndColumns.getTablesAndColumns();

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData()).hasSize(2);
          
          TableMetadataResponseDto table1 = response.getData().get(0);
          assertThat(table1.getTableName()).isEqualTo("table1");
          assertThat(table1.getTableSchema()).isEqualTo("test_db");
          assertThat(table1.getTableType()).isEqualTo("BASE TABLE");
          assertThat(table1.getColumns()).hasSize(2);
          assertThat(table1.getColumns().get(0).getColumnName()).isEqualTo("id");
          assertThat(table1.getColumns().get(1).getColumnName()).isEqualTo("name");
          
          TableMetadataResponseDto table2 = response.getData().get(1);
          assertThat(table2.getTableName()).isEqualTo("table2");
          assertThat(table2.getColumns()).isEmpty();
          
          verify(queryService).getTablesAndColumns();
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldHandleEmptyTablesList(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      when(queryService.getTablesAndColumns()).thenReturn(Single.just(Collections.emptyList()));

      CompletionStage<Response<List<TableMetadataResponseDto>>> result = getTablesAndColumns.getTablesAndColumns();

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData()).isEmpty();
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldHandleError(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      when(queryService.getTablesAndColumns()).thenReturn(Single.error(new RuntimeException("Database error")));

      CompletionStage<Response<List<TableMetadataResponseDto>>> result = getTablesAndColumns.getTablesAndColumns();

      result.whenComplete((response, error) -> {
        testContext.verify(() -> {
          assertThat(error).isNotNull();
        });
        testContext.completeNow();
      });
    });
  }
}


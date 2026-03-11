package org.dreamhorizon.pulseserver.client.chclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.insert.InsertResponse;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.dreamhorizon.pulseserver.model.ClickhouseProjectCredentials;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "rawtypes"})
class ClickhouseQueryServiceTest {

  @Mock
  ClickhouseReadClient clickhouseReadClient;

  @Mock
  ClickhouseWriteClient clickhouseWriteClient;

  @Mock
  ClickhouseProjectConnectionPoolManager clickhouseProjectConnectionPoolManager;

  @Mock
  ClickhouseProjectCredentialsDao clickhouseProjectCredentialsDao;

  ClickhouseQueryService clickhouseQueryService;

  @BeforeEach
  void setUp() {
    clickhouseQueryService = new ClickhouseQueryService(
        clickhouseReadClient,
        clickhouseWriteClient,
        clickhouseProjectConnectionPoolManager,
        clickhouseProjectCredentialsDao);
  }

  @Nested
  class ExecuteQueryOrCreateJob {

    @Test
    void shouldFailWithIllegalArgumentExceptionWhenProjectIdIsNull() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .projectId(null)
          .build();

      Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> result =
          clickhouseQueryService.executeQueryOrCreateJob(config);

      result.test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("Project ID must be provided"));
    }

    @Test
    void shouldFailWithIllegalStateExceptionWhenCredentialsNotFound() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .projectId("proj_123")
          .build();

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_123"))
          .thenReturn(Maybe.empty());

      Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>> result =
          clickhouseQueryService.executeQueryOrCreateJob(config);

      result.test()
          .assertError(IllegalStateException.class)
          .assertError(e -> e.getMessage().contains("No ClickHouse credentials found for project"));
      verify(clickhouseProjectCredentialsDao).getCredentialsByProjectId("proj_123");
    }

    @Test
    void shouldReturnQueryResultsWhenCredentialsAndPoolAreAvailable() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT col1, col2 FROM t")
          .projectId("proj_123")
          .build();

      ClickhouseProjectCredentials credentials = ClickhouseProjectCredentials.builder()
          .projectId("proj_123")
          .clickhouseUsername("ch_user")
          .clickhousePasswordEncrypted("encrypted_pass")
          .build();

      Row mockRow = org.mockito.Mockito.mock(Row.class);
      when(mockRow.get(0)).thenReturn("val1");
      when(mockRow.get(1)).thenReturn("val2");

      RowMetadata mockRowMetadata = org.mockito.Mockito.mock(RowMetadata.class);
      io.r2dbc.spi.ColumnMetadata col1 = org.mockito.Mockito.mock(io.r2dbc.spi.ColumnMetadata.class);
      io.r2dbc.spi.ColumnMetadata col2 = org.mockito.Mockito.mock(io.r2dbc.spi.ColumnMetadata.class);
      when(col1.getName()).thenReturn("col1");
      when(col2.getName()).thenReturn("col2");
      doReturn(List.of(col1, col2)).when(mockRowMetadata).getColumnMetadatas();

      Result mockResult = org.mockito.Mockito.mock(Result.class);
      doAnswer(invocation -> {
        BiFunction<Row, RowMetadata, ?> mapper = invocation.getArgument(0);
        Object mapped = mapper.apply(mockRow, mockRowMetadata);
        return Flux.just(mapped);
      }).when(mockResult).map(org.mockito.ArgumentMatchers.<BiFunction<Row, RowMetadata, ?>>any());

      Connection mockConnection = org.mockito.Mockito.mock(Connection.class);
      io.r2dbc.spi.Statement mockStatement = org.mockito.Mockito.mock(io.r2dbc.spi.Statement.class);
      when(mockConnection.createStatement("SELECT col1, col2 FROM t")).thenReturn(mockStatement);
      doReturn(Mono.just(mockResult)).when(mockStatement).execute();
      when(mockConnection.close()).thenReturn(Mono.empty());

      ConnectionPool mockPool = org.mockito.Mockito.mock(ConnectionPool.class);
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_123"))
          .thenReturn(Maybe.just(credentials));
      when(clickhouseProjectConnectionPoolManager.getPoolForProject(
          eq("proj_123"), eq("ch_user"), eq("encrypted_pass")))
          .thenReturn(mockPool);

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response =
          clickhouseQueryService.executeQueryOrCreateJob(config).blockingGet();

      assertThat(response).isNotNull();
      assertThat(response.isJobComplete()).isTrue();
      assertThat(response.getData()).isNotNull();
      assertThat(response.getData().getSchema()).isNotNull();
      assertThat(response.getData().getSchema().getFields())
          .extracting(GetRawUserEventsResponseDto.Field::getName)
          .containsExactly("col1", "col2");
      assertThat(response.getData().getRows()).hasSize(1);
      assertThat(response.getData().getRows().get(0).getRowFields())
          .extracting(GetRawUserEventsResponseDto.RowField::getValue)
          .containsExactly("val1", "val2");
      assertThat(response.getData().getTotalRows()).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyRowsWhenQueryReturnsNoResults() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .projectId("proj_empty")
          .build();

      ClickhouseProjectCredentials credentials = ClickhouseProjectCredentials.builder()
          .projectId("proj_empty")
          .clickhouseUsername("ch_user")
          .clickhousePasswordEncrypted("pass")
          .build();

      Result mockResult = org.mockito.Mockito.mock(Result.class);
      doAnswer(invocation -> Flux.<GetRawUserEventsResponseDto.Row>empty())
          .when(mockResult).map(org.mockito.ArgumentMatchers.<BiFunction<Row, RowMetadata, ?>>any());

      Connection mockConnection = org.mockito.Mockito.mock(Connection.class);
      io.r2dbc.spi.Statement mockStatement = org.mockito.Mockito.mock(io.r2dbc.spi.Statement.class);
      when(mockConnection.createStatement("SELECT 1")).thenReturn(mockStatement);
      doReturn(Mono.just(mockResult)).when(mockStatement).execute();
      when(mockConnection.close()).thenReturn(Mono.empty());

      ConnectionPool mockPool = org.mockito.Mockito.mock(ConnectionPool.class);
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_empty"))
          .thenReturn(Maybe.just(credentials));
      when(clickhouseProjectConnectionPoolManager.getPoolForProject(
          eq("proj_empty"), eq("ch_user"), eq("pass")))
          .thenReturn(mockPool);

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response =
          clickhouseQueryService.executeQueryOrCreateJob(config).blockingGet();

      assertThat(response).isNotNull();
      assertThat(response.isJobComplete()).isTrue();
      assertThat(response.getData().getRows()).isEmpty();
      assertThat(response.getData().getTotalRows()).isEqualTo(0L);
    }

    @Test
    void shouldPropagateErrorWhenPoolCreationFails() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .projectId("proj_err")
          .build();

      ClickhouseProjectCredentials credentials = ClickhouseProjectCredentials.builder()
          .projectId("proj_err")
          .clickhouseUsername("ch_user")
          .clickhousePasswordEncrypted("pass")
          .build();

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_err"))
          .thenReturn(Maybe.just(credentials));
      when(clickhouseProjectConnectionPoolManager.getPoolForProject(
          eq("proj_err"), eq("ch_user"), eq("pass")))
          .thenThrow(new RuntimeException("Pool creation failed"));

      clickhouseQueryService.executeQueryOrCreateJob(config)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("Pool creation failed"));
    }

    @Test
    void shouldPropagateErrorWhenQueryExecutionFails() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .projectId("proj_exec_err")
          .build();

      ClickhouseProjectCredentials credentials = ClickhouseProjectCredentials.builder()
          .projectId("proj_exec_err")
          .clickhouseUsername("ch_user")
          .clickhousePasswordEncrypted("pass")
          .build();

      Connection mockConnection = org.mockito.Mockito.mock(Connection.class);
      io.r2dbc.spi.Statement mockStatement = org.mockito.Mockito.mock(io.r2dbc.spi.Statement.class);
      when(mockConnection.createStatement("SELECT 1")).thenReturn(mockStatement);
      doReturn(Flux.<Result>error(new RuntimeException("Query failed"))).when(mockStatement).execute();
      when(mockConnection.close()).thenReturn(Mono.empty());

      ConnectionPool mockPool = org.mockito.Mockito.mock(ConnectionPool.class);
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_exec_err"))
          .thenReturn(Maybe.just(credentials));
      when(clickhouseProjectConnectionPoolManager.getPoolForProject(
          eq("proj_exec_err"), eq("ch_user"), eq("pass")))
          .thenReturn(mockPool);

      clickhouseQueryService.executeQueryOrCreateJob(config)
          .test()
          .assertError(Exception.class)
          .assertError(e -> e.getMessage().contains("Failed to execute tenant query"));
    }
  }

  @Nested
  class ExecuteQueryOrCreateJobGeneric {

    @Test
    void shouldFailWithIllegalArgumentExceptionWhenProjectIdIsNull() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .projectId(null)
          .build();

      Single<org.dreamhorizon.pulseserver.model.QueryResultResponse<TestRow>> result =
          clickhouseQueryService.executeQueryOrCreateJob(config, TestRow.class);

      result.test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("Project ID must be provided"));
    }

    @Test
    void shouldFailWithIllegalStateExceptionWhenCredentialsNotFound() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .projectId("proj_456")
          .build();

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_456"))
          .thenReturn(Maybe.empty());

      Single<org.dreamhorizon.pulseserver.model.QueryResultResponse<TestRow>> result =
          clickhouseQueryService.executeQueryOrCreateJob(config, TestRow.class);

      result.test()
          .assertError(IllegalStateException.class)
          .assertError(e -> e.getMessage().contains("No ClickHouse credentials found for project"));
      verify(clickhouseProjectCredentialsDao).getCredentialsByProjectId("proj_456");
    }

    @Test
    void shouldReturnMappedRowsWhenCredentialsAndPoolAreAvailable() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT col1 FROM t")
          .projectId("proj_gen")
          .build();

      ClickhouseProjectCredentials credentials = ClickhouseProjectCredentials.builder()
          .projectId("proj_gen")
          .clickhouseUsername("ch_user")
          .clickhousePasswordEncrypted("pass")
          .build();

      Row mockRow = org.mockito.Mockito.mock(Row.class);
      when(mockRow.get(0)).thenReturn("mapped_value");

      RowMetadata mockRowMetadata = org.mockito.Mockito.mock(RowMetadata.class);
      io.r2dbc.spi.ColumnMetadata colMeta = org.mockito.Mockito.mock(io.r2dbc.spi.ColumnMetadata.class);
      when(colMeta.getName()).thenReturn("col1");
      doReturn(List.of(colMeta)).when(mockRowMetadata).getColumnMetadatas();

      Result mockResult = org.mockito.Mockito.mock(Result.class);
      doAnswer(invocation -> {
        BiFunction<Row, RowMetadata, ?> mapper = invocation.getArgument(0);
        Object mapped = mapper.apply(mockRow, mockRowMetadata);
        return Flux.just(mapped);
      }).when(mockResult).map(org.mockito.ArgumentMatchers.<BiFunction<Row, RowMetadata, ?>>any());

      Connection mockConnection = org.mockito.Mockito.mock(Connection.class);
      io.r2dbc.spi.Statement mockStatement = org.mockito.Mockito.mock(io.r2dbc.spi.Statement.class);
      when(mockConnection.createStatement("SELECT col1 FROM t")).thenReturn(mockStatement);
      doReturn(Mono.just(mockResult)).when(mockStatement).execute();
      when(mockConnection.close()).thenReturn(Mono.empty());

      ConnectionPool mockPool = org.mockito.Mockito.mock(ConnectionPool.class);
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_gen"))
          .thenReturn(Maybe.just(credentials));
      when(clickhouseProjectConnectionPoolManager.getPoolForProject(
          eq("proj_gen"), eq("ch_user"), eq("pass")))
          .thenReturn(mockPool);

      QueryResultResponse<TestRow> response =
          clickhouseQueryService.executeQueryOrCreateJob(config, TestRow.class).blockingGet();

      assertThat(response).isNotNull();
      assertThat(response.getJobComplete()).isTrue();
      assertThat(response.getRows()).hasSize(1);
      assertThat(response.getRows().get(0).getCol1()).isEqualTo("mapped_value");
    }

    @Test
    void shouldReturnEmptyRowsWhenGenericQueryReturnsNoResults() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT col1 FROM t")
          .projectId("proj_gen_empty")
          .build();

      ClickhouseProjectCredentials credentials = ClickhouseProjectCredentials.builder()
          .projectId("proj_gen_empty")
          .clickhouseUsername("ch_user")
          .clickhousePasswordEncrypted("pass")
          .build();

      Result mockResult = org.mockito.Mockito.mock(Result.class);
      doAnswer(invocation -> Flux.<java.util.Map<String, Object>>empty())
          .when(mockResult).map(org.mockito.ArgumentMatchers.<BiFunction<Row, RowMetadata, ?>>any());

      Connection mockConnection = org.mockito.Mockito.mock(Connection.class);
      io.r2dbc.spi.Statement mockStatement = org.mockito.Mockito.mock(io.r2dbc.spi.Statement.class);
      when(mockConnection.createStatement("SELECT col1 FROM t")).thenReturn(mockStatement);
      doReturn(Mono.just(mockResult)).when(mockStatement).execute();
      when(mockConnection.close()).thenReturn(Mono.empty());

      ConnectionPool mockPool = org.mockito.Mockito.mock(ConnectionPool.class);
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_gen_empty"))
          .thenReturn(Maybe.just(credentials));
      when(clickhouseProjectConnectionPoolManager.getPoolForProject(
          eq("proj_gen_empty"), eq("ch_user"), eq("pass")))
          .thenReturn(mockPool);

      QueryResultResponse<TestRow> response =
          clickhouseQueryService.executeQueryOrCreateJob(config, TestRow.class).blockingGet();

      assertThat(response).isNotNull();
      assertThat(response.getJobComplete()).isTrue();
      assertThat(response.getRows()).isEmpty();
    }

    @Test
    void shouldPropagateErrorWhenGenericQueryExecutionFails() {
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .projectId("proj_gen_err")
          .build();

      ClickhouseProjectCredentials credentials = ClickhouseProjectCredentials.builder()
          .projectId("proj_gen_err")
          .clickhouseUsername("ch_user")
          .clickhousePasswordEncrypted("pass")
          .build();

      Connection mockConnection = org.mockito.Mockito.mock(Connection.class);
      io.r2dbc.spi.Statement mockStatement = org.mockito.Mockito.mock(io.r2dbc.spi.Statement.class);
      when(mockConnection.createStatement("SELECT 1")).thenReturn(mockStatement);
      doReturn(Flux.<Result>error(new RuntimeException("CH error"))).when(mockStatement).execute();
      when(mockConnection.close()).thenReturn(Mono.empty());

      ConnectionPool mockPool = org.mockito.Mockito.mock(ConnectionPool.class);
      when(mockPool.create()).thenReturn(Mono.just(mockConnection));

      when(clickhouseProjectCredentialsDao.getCredentialsByProjectId("proj_gen_err"))
          .thenReturn(Maybe.just(credentials));
      when(clickhouseProjectConnectionPoolManager.getPoolForProject(
          eq("proj_gen_err"), eq("ch_user"), eq("pass")))
          .thenReturn(mockPool);

      clickhouseQueryService.executeQueryOrCreateJob(config, TestRow.class)
          .test()
          .assertError(Exception.class)
          .assertError(e -> e.getMessage().contains("Failed to execute tenant generic query"));
    }
  }

  @Nested
  class InsertStackTraces {

    @Test
    void shouldReturnWrittenRowsCount() {
      List<StackTraceEvent> events = Collections.singletonList(
          StackTraceEvent.builder()
              .title("Test")
              .timestamp("2024-01-01T00:00:00")
              .build());

      InsertResponse mockResponse = org.mockito.Mockito.mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(42L);
      when(clickhouseWriteClient.insert(anyList())).thenReturn(Single.just(mockResponse));

      Long written = clickhouseQueryService.insertStackTraces(events).blockingGet();

      assertThat(written).isEqualTo(42L);
      verify(clickhouseWriteClient).insert(eq(events));
    }

    @Test
    void shouldPropagateErrorFromWriteClient() {
      List<StackTraceEvent> events = Collections.emptyList();
      when(clickhouseWriteClient.insert(anyList()))
          .thenReturn(Single.error(new RuntimeException("Write failed")));

      clickhouseQueryService.insertStackTraces(events)
          .test()
          .assertError(RuntimeException.class)
          .assertError(e -> e.getMessage().contains("Write failed"));
    }
  }

  @SuppressWarnings("unused")
  public static class TestRow {
    private String col1;

    public String getCol1() {
      return col1;
    }

    public void setCol1(String col1) {
      this.col1 = col1;
    }
  }
}

package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.insert.InsertResponse;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Statement;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.ClickhouseCredentialsDao;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ClickhouseQueryServiceIntegrationTest {

  @Mock
  private ClickhouseReadClient clickhouseReadClient;

  @Mock
  private ClickhouseWriteClient clickhouseWriteClient;

  @Mock
  private ClickhouseTenantConnectionPoolManager poolManager;

  @Mock
  private ClickhouseCredentialsDao credentialsDao;

  @Mock
  private ConnectionPool connectionPool;

  @Mock
  private Connection connection;

  @Mock
  private Statement statement;

  private ClickhouseQueryService queryService;

  @BeforeEach
  void setup() {
    queryService = new ClickhouseQueryService(
        clickhouseReadClient,
        clickhouseWriteClient,
        poolManager,
        credentialsDao
    );
  }

  private ClickhouseCredentials createMockCredentials() {
    return ClickhouseCredentials.builder()
        .tenantId("test_tenant")
        .clickhouseUsername("user_test")
        .clickhousePassword("password123")
        .isActive(true)
        .build();
  }

  private RowMetadata createMockMetadata(String... columnNames) {
    RowMetadata mockMetadata = mock(RowMetadata.class);
    List<ColumnMetadata> columnList = new ArrayList<>();
    for (String name : columnNames) {
      ColumnMetadata col = mock(ColumnMetadata.class);
      when(col.getName()).thenReturn(name);
      columnList.add(col);
    }
    // Use doReturn to handle wildcard generic types
    doReturn(columnList).when(mockMetadata).getColumnMetadatas();
    return mockMetadata;
  }

  @Nested
  class TestExecuteQueryWithResults {

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteQueryAndMapResults() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT id, name FROM test")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);

      // Create mock result with actual data
      Result mockResult = mock(Result.class);
      Row mockRow = mock(Row.class);
      RowMetadata mockMetadata = createMockMetadata("id", "name");

      when(mockRow.get(0)).thenReturn("1");
      when(mockRow.get(1)).thenReturn("TestName");

      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when((Publisher<Result>) statement.execute()).thenReturn(Flux.just(mockResult));
      when(connection.close()).thenReturn(Mono.empty());

      // Make result.map actually execute the BiFunction
      when(mockResult.map(any(BiFunction.class))).thenAnswer(invocation -> {
        BiFunction<Row, RowMetadata, ?> mapper = invocation.getArgument(0);
        Object mappedRow = mapper.apply(mockRow, mockMetadata);
        return Flux.just(mappedRow);
      });

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response = 
          queryService.executeQueryOrCreateJob(config).blockingGet();

      assertNotNull(response);
      assertTrue(response.isJobComplete());
      assertNotNull(response.getData());
      assertEquals(1, response.getData().getTotalRows());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteQueryWithMultipleRows() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT id FROM test")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);

      Result mockResult = mock(Result.class);
      Row mockRow1 = mock(Row.class);
      Row mockRow2 = mock(Row.class);
      RowMetadata mockMetadata = createMockMetadata("id");

      when(mockRow1.get(0)).thenReturn("1");
      when(mockRow2.get(0)).thenReturn("2");

      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when((Publisher<Result>) statement.execute()).thenReturn(Flux.just(mockResult));
      when(connection.close()).thenReturn(Mono.empty());

      // Return multiple rows
      when(mockResult.map(any(BiFunction.class))).thenAnswer(invocation -> {
        BiFunction<Row, RowMetadata, ?> mapper = invocation.getArgument(0);
        Object row1 = mapper.apply(mockRow1, mockMetadata);
        Object row2 = mapper.apply(mockRow2, mockMetadata);
        return Flux.just(row1, row2);
      });

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response = 
          queryService.executeQueryOrCreateJob(config).blockingGet();

      assertNotNull(response);
      assertEquals(2, response.getData().getTotalRows());
      assertEquals(2, response.getData().getRows().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleEmptyResultSet() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT id FROM test WHERE 1=0")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);

      Result mockResult = mock(Result.class);

      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when((Publisher<Result>) statement.execute()).thenReturn(Flux.just(mockResult));
      when(connection.close()).thenReturn(Mono.empty());

      when(mockResult.map(any(BiFunction.class))).thenReturn(Flux.empty());

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response = 
          queryService.executeQueryOrCreateJob(config).blockingGet();

      assertNotNull(response);
      assertEquals(0, response.getData().getTotalRows());
    }
  }

  @Nested
  class TestExecuteGenericQueryWithResults {

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteGenericQueryAndMapToClass() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT name FROM test")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);

      Result mockResult = mock(Result.class);
      Row mockRow = mock(Row.class);
      RowMetadata mockMetadata = createMockMetadata("name");

      when(mockRow.get(0)).thenReturn("TestValue");

      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when((Publisher<Result>) statement.execute()).thenReturn(Flux.just(mockResult));
      when(connection.close()).thenReturn(Mono.empty());

      when(mockResult.map(any(BiFunction.class))).thenAnswer(invocation -> {
        BiFunction<Row, RowMetadata, ?> mapper = invocation.getArgument(0);
        Object mappedRow = mapper.apply(mockRow, mockMetadata);
        return Flux.just(mappedRow);
      });

      QueryResultResponse<TestDto> response = 
          queryService.executeQueryOrCreateJob(config, TestDto.class).blockingGet();

      assertNotNull(response);
      assertTrue(response.getJobComplete());
      assertNotNull(response.getRows());
      assertEquals(1, response.getRows().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteGenericQueryWithMultipleColumns() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT id, name, value FROM test")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);

      Result mockResult = mock(Result.class);
      Row mockRow = mock(Row.class);
      RowMetadata mockMetadata = createMockMetadata("id", "name", "value");

      when(mockRow.get(0)).thenReturn("1");
      when(mockRow.get(1)).thenReturn("Test");
      when(mockRow.get(2)).thenReturn("100");

      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when((Publisher<Result>) statement.execute()).thenReturn(Flux.just(mockResult));
      when(connection.close()).thenReturn(Mono.empty());

      when(mockResult.map(any(BiFunction.class))).thenAnswer(invocation -> {
        BiFunction<Row, RowMetadata, ?> mapper = invocation.getArgument(0);
        Object mappedRow = mapper.apply(mockRow, mockMetadata);
        return Flux.just(mappedRow);
      });

      QueryResultResponse<TestDto> response = 
          queryService.executeQueryOrCreateJob(config, TestDto.class).blockingGet();

      assertNotNull(response);
      assertEquals(1, response.getRows().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleEmptyGenericResultSet() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT name FROM test WHERE 1=0")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);

      Result mockResult = mock(Result.class);

      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when((Publisher<Result>) statement.execute()).thenReturn(Flux.just(mockResult));
      when(connection.close()).thenReturn(Mono.empty());

      when(mockResult.map(any(BiFunction.class))).thenReturn(Flux.empty());

      QueryResultResponse<TestDto> response = 
          queryService.executeQueryOrCreateJob(config, TestDto.class).blockingGet();

      assertNotNull(response);
      assertTrue(response.getRows().isEmpty());
    }
  }

  @Nested
  class TestInsertOperations {

    @Test
    void shouldInsertStackTracesSuccessfully() {
      StackTraceEvent event1 = mock(StackTraceEvent.class);
      StackTraceEvent event2 = mock(StackTraceEvent.class);
      List<StackTraceEvent> events = Arrays.asList(event1, event2);

      InsertResponse mockResponse = mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(2L);
      when(clickhouseWriteClient.insert(events)).thenReturn(Single.just(mockResponse));

      Long result = queryService.insertStackTraces(events).blockingGet();

      assertEquals(2L, result);
    }
  }

  @Nested
  class TestErrorHandling {

    @Test
    void shouldHandleCredentialsError() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .tenantId(tenantId)
          .build();

      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.error(new RuntimeException("Credentials not found")));

      queryService.executeQueryOrCreateJob(config)
          .test()
          .assertError(throwable -> throwable.getMessage().contains("Credentials not found"));
    }

    @Test
    void shouldHandleConnectionError() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);
      when(connectionPool.create()).thenReturn(Mono.error(new RuntimeException("Connection failed")));

      queryService.executeQueryOrCreateJob(config)
          .test()
          .assertError(throwable -> throwable.getMessage().contains("Failed to execute tenant query"));
    }
  }

  // Test DTO
  public static class TestDto {
    private String id;
    private String name;
    private String value;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
  }
}

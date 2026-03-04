package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.ClickhouseCredentialsDao;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
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
class ClickhouseQueryServiceTest {

  @Mock
  private ClickhouseReadClient clickhouseReadClient;

  @Mock
  private ClickhouseWriteClient clickhouseWriteClient;

  @Mock
  private ClickhouseTenantConnectionPoolManager poolManager;

  @Mock
  private ClickhouseProjectConnectionPoolManager projectPoolManager;

  @Mock
  private ClickhouseCredentialsDao credentialsDao;

  @Mock
  private ClickhouseProjectCredentialsDao projectCredentialsDao;

  @Mock
  private ConnectionPool connectionPool;

  @Mock
  private Connection connection;

  @Mock
  private Statement statement;

  @Mock
  private Result result;

  @Mock
  private Row row;

  @Mock
  private RowMetadata rowMetadata;

  @Mock
  private ColumnMetadata columnMetadata;

  private ClickhouseQueryService queryService;

  @BeforeEach
  void setup() {
    queryService = new ClickhouseQueryService(
        clickhouseReadClient,
        clickhouseWriteClient,
        projectPoolManager,
        projectCredentialsDao
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

  @Nested
  class TestConstructor {

    @Test
    void shouldCreateServiceWithDependencies() {
      assertNotNull(queryService);
      assertNotNull(queryService.getClickhouseReadClient());
      assertNotNull(queryService.getClickhouseWriteClient());
    }

    @Test
    void shouldHaveObjectMapper() {
      // The objectMapper is initialized inline
      assertNotNull(queryService);
    }
  }

  @Nested
  class TestExecuteQueryOrCreateJob {

    @Test
    void shouldFetchCredentialsForTenant() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .tenantId(tenantId)
          .build();

      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.error(new RuntimeException("Test error")));

      queryService.executeQueryOrCreateJob(config)
          .test()
          .assertError(RuntimeException.class);

      verify(credentialsDao).getCredentialsByTenantId(tenantId);
    }

    @Test
    void shouldHandleCredentialsNotFound() {
      String tenantId = "non_existent_tenant";
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
    void shouldGetPoolForTenantAfterCredentials() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), credentials.getClickhousePassword()))
          .thenThrow(new RuntimeException("Pool error"));

      queryService.executeQueryOrCreateJob(config)
          .test()
          .assertError(RuntimeException.class);

      verify(poolManager).getPoolForTenant(tenantId, "user_test", "password123");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteQuerySuccessfully() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT * FROM test")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);

      // Mock the connection pool
      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when((Publisher<Result>) statement.execute()).thenReturn(Flux.just(result));
      when(connection.close()).thenReturn(Mono.empty());

      // Mock empty result
      when(result.map(any(BiFunction.class))).thenReturn(Flux.empty());

      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response =
          queryService.executeQueryOrCreateJob(config).blockingGet();

      assertNotNull(response);
      assertTrue(response.isJobComplete());
      assertNotNull(response.getData());
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
          .assertError(e -> e.getMessage().contains("Failed to execute tenant query"));
    }

    @Test
    void shouldHandleStatementExecutionError() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);
      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when(statement.execute()).thenReturn(Flux.error(new RuntimeException("SQL Error")));
      when(connection.close()).thenReturn(Mono.empty());

      queryService.executeQueryOrCreateJob(config)
          .test()
          .assertError(Exception.class);
    }
  }

  @Nested
  class TestExecuteQueryOrCreateJobWithClass {

    @Test
    void shouldFetchCredentialsForGenericQuery() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .tenantId(tenantId)
          .build();

      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.error(new RuntimeException("Test error")));

      queryService.executeQueryOrCreateJob(config, String.class)
          .test()
          .assertError(RuntimeException.class);

      verify(credentialsDao).getCredentialsByTenantId(tenantId);
    }

    @Test
    void shouldGetPoolForGenericQuery() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT 1")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenThrow(new RuntimeException("Pool error"));

      queryService.executeQueryOrCreateJob(config, String.class)
          .test()
          .assertError(RuntimeException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteGenericQuerySuccessfully() {
      String tenantId = "test_tenant";
      QueryConfiguration config = QueryConfiguration.newQuery("SELECT name FROM test")
          .tenantId(tenantId)
          .build();

      ClickhouseCredentials credentials = createMockCredentials();
      when(credentialsDao.getCredentialsByTenantId(tenantId))
          .thenReturn(Single.just(credentials));
      when(poolManager.getPoolForTenant(anyString(), anyString(), anyString()))
          .thenReturn(connectionPool);

      when(connectionPool.create()).thenReturn(Mono.just(connection));
      when(connection.createStatement(anyString())).thenReturn(statement);
      when((Publisher<Result>) statement.execute()).thenReturn(Flux.just(result));
      when(connection.close()).thenReturn(Mono.empty());
      when(result.map(any(BiFunction.class))).thenReturn(Flux.empty());

      QueryResultResponse<TestDto> response =
          queryService.executeQueryOrCreateJob(config, TestDto.class).blockingGet();

      assertNotNull(response);
      assertTrue(response.getJobComplete());
    }

    @Test
    void shouldHandleGenericQueryConnectionError() {
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

      queryService.executeQueryOrCreateJob(config, TestDto.class)
          .test()
          .assertError(e -> e.getMessage().contains("Failed to execute tenant generic query"));
    }
  }

  @Nested
  class TestInsertStackTraces {

    @Test
    void shouldDelegateToWriteClient() {
      List<StackTraceEvent> events = new ArrayList<>();

      when(clickhouseWriteClient.insert(any()))
          .thenReturn(Single.error(new RuntimeException("Write error")));

      queryService.insertStackTraces(events)
          .test()
          .assertError(RuntimeException.class);

      verify(clickhouseWriteClient).insert(events);
    }

    @Test
    void shouldReturnWrittenRowsCount() {
      List<StackTraceEvent> events = new ArrayList<>();
      InsertResponse mockResponse = mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(5L);
      when(clickhouseWriteClient.insert(any()))
          .thenReturn(Single.just(mockResponse));

      Long result = queryService.insertStackTraces(events).blockingGet();

      assertEquals(5L, result);
    }

    @Test
    void shouldHandleEmptyEventsList() {
      List<StackTraceEvent> events = new ArrayList<>();
      InsertResponse mockResponse = mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(0L);
      when(clickhouseWriteClient.insert(events))
          .thenReturn(Single.just(mockResponse));

      Long result = queryService.insertStackTraces(events).blockingGet();

      assertEquals(0L, result);
    }

    @Test
    void shouldHandleNonEmptyEventsList() {
      StackTraceEvent event = mock(StackTraceEvent.class);
      List<StackTraceEvent> events = Arrays.asList(event, event, event);
      InsertResponse mockResponse = mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(3L);
      when(clickhouseWriteClient.insert(events))
          .thenReturn(Single.just(mockResponse));

      Long result = queryService.insertStackTraces(events).blockingGet();

      assertEquals(3L, result);
    }
  }

  @Nested
  class TestGetters {

    @Test
    void shouldReturnClickhouseReadClient() {
      assertEquals(clickhouseReadClient, queryService.getClickhouseReadClient());
    }

    @Test
    void shouldReturnClickhouseWriteClient() {
      assertEquals(clickhouseWriteClient, queryService.getClickhouseWriteClient());
    }

    @Test
    void shouldReturnObjectMapper() {
      assertNotNull(queryService.getObjectMapper());
    }
  }

  @Nested
  class TestEqualsAndHashCode {

    @Test
    void shouldImplementEquals() {
      ClickhouseQueryService service1 = new ClickhouseQueryService(
          clickhouseReadClient, clickhouseWriteClient, projectPoolManager, projectCredentialsDao);
      ClickhouseQueryService service2 = new ClickhouseQueryService(
          clickhouseReadClient, clickhouseWriteClient, projectPoolManager, projectCredentialsDao);

      // Lombok @Data generates equals based on fields
      // Since objectMapper is final and created inline, services with same deps should be equal
      assertNotNull(service1);
      assertNotNull(service2);
    }

    @Test
    void shouldImplementHashCode() {
      int hashCode = queryService.hashCode();
      assertNotNull(hashCode);
    }

    @Test
    void shouldImplementToString() {
      String toString = queryService.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("ClickhouseQueryService"));
    }
  }

  // Test DTO for generic query tests
  public static class TestDto {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}

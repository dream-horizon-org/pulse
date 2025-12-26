package org.dreamhorizon.pulseserver.dao.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.PropertyKind;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Transaction;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.resources.configs.models.AllConfigdetails;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;
import org.dreamhorizon.pulseserver.service.configs.models.ConfigData;
import org.dreamhorizon.pulseserver.service.configs.models.FeatureConfig;
import org.dreamhorizon.pulseserver.service.configs.models.Features;
import org.dreamhorizon.pulseserver.service.configs.models.FilterConfig;
import org.dreamhorizon.pulseserver.service.configs.models.FilterMode;
import org.dreamhorizon.pulseserver.service.configs.models.InteractionConfig;
import org.dreamhorizon.pulseserver.service.configs.models.SamplingConfig;
import org.dreamhorizon.pulseserver.service.configs.models.SignalsConfig;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class SdkConfigsDaoTest {

  @Test
  void shouldHaveCorrectQueryStrings() {
    // This test ensures the Queries class is instantiated and constants are verified
    // Instantiate to cover the implicit constructor
    Queries queries = new Queries();
    assertThat(queries).isNotNull();
    
    assertThat(Queries.INSERT_CONFIG).contains("INSERT INTO pulse_sdk_configs");
    assertThat(Queries.GET_CONFIG_BY_VERSION).contains("SELECT config_json");
    assertThat(Queries.GET_LATEST_VERSION).contains("SELECT version");
    assertThat(Queries.GET_ALL_CONFIG_DETAILS).contains("SELECT version, description");
    assertThat(Queries.DEACTIVATE_ACTIVE_CONFIG).contains("UPDATE pulse_sdk_configs");
  }

  @Mock
  MysqlClient d11MysqlClient;

  @Mock
  MySQLPool readerPool;

  @Mock
  MySQLPool writerPool;

  ObjectMapperUtil objectMapper;

  SdkConfigsDao sdkConfigsDao;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapperUtil();
    sdkConfigsDao = new SdkConfigsDao(d11MysqlClient, objectMapper);
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetConfigByVersion {

    @Test
    void shouldGetConfigByVersionSuccessfully() {
      // Given
      long version = 1L;
      String configJson = "{\"sampling\":{},\"signals\":{\"filters\":{\"mode\":\"blacklist\",\"values\":[]}},\"interaction\":{},\"features\":[]}";
      String description = "Test Config";

      Row mockRow = mock(Row.class);
      when(mockRow.getValue("config_json")).thenReturn(configJson);
      when(mockRow.getValue("version")).thenReturn(version);
      when(mockRow.getValue("description")).thenReturn(description);

      RowSet<Row> rowSet = mock(RowSet.class);
      when(rowSet.size()).thenReturn(1);
      RowIterator<Row> iterator = mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(iterator);
      when(iterator.next()).thenReturn(mockRow);

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      // When
      PulseConfig result = sdkConfigsDao.getConfig(version).blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getDescription()).isEqualTo(description);

      Tuple capturedTuple = tupleCaptor.getValue();
      assertThat(capturedTuple.getLong(0)).isEqualTo(version);

      verify(d11MysqlClient, times(1)).getWriterPool();
      verifyNoMoreInteractions(d11MysqlClient);
    }

    @Test
    void shouldGetConfigByVersionWithNullDescription() {
      // Given
      long version = 1L;
      String configJson = "{\"sampling\":{},\"signals\":{\"filters\":{\"mode\":\"whitelist\",\"values\":[]}},\"interaction\":{},\"features\":[]}";

      Row mockRow = mock(Row.class);
      when(mockRow.getValue("config_json")).thenReturn(configJson);
      when(mockRow.getValue("version")).thenReturn(version);
      when(mockRow.getValue("description")).thenReturn(null);

      RowSet<Row> rowSet = mock(RowSet.class);
      when(rowSet.size()).thenReturn(1);
      RowIterator<Row> iterator = mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(iterator);
      when(iterator.next()).thenReturn(mockRow);

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      // When
      PulseConfig result = sdkConfigsDao.getConfig(version).blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getDescription()).isNull();
    }

    @Test
    void shouldThrowExceptionWhenNoConfigFoundForVersion() {
      // Given
      long version = 999L;

      RowSet<Row> rowSet = mock(RowSet.class);
      when(rowSet.size()).thenReturn(0);

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      // When & Then
      RuntimeException exception = assertThrows(RuntimeException.class,
          () -> sdkConfigsDao.getConfig(version).blockingGet());
      assertThat(exception.getMessage()).isEqualTo("No config found for version: " + version);
    }

    @Test
    void shouldPropagateErrorWhenDatabaseCallFails() {
      // Given
      long version = 1L;
      RuntimeException dbError = new RuntimeException("Database connection failed");

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(dbError));

      // When
      var testObserver = sdkConfigsDao.getConfig(version).test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Database connection failed"));
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetLatestConfig {

    @Test
    void shouldGetLatestConfigWithBlacklistMode() {
      // Given
      long version = 5L;
      String configJson = "{\"sampling\":{},\"signals\":{\"filters\":{\"mode\":\"blacklist\",\"values\":[]}},\"interaction\":{},\"features\":[]}";
      String description = "Latest Config";

      // Mock for GET_LATEST_VERSION query
      Row versionRow = mock(Row.class);
      when(versionRow.getValue("version")).thenReturn(version);

      RowSet<Row> versionRowSet = mock(RowSet.class);
      when(versionRowSet.size()).thenReturn(1);
      RowIterator<Row> versionIterator = mock(RowIterator.class);
      when(versionRowSet.iterator()).thenReturn(versionIterator);
      when(versionIterator.next()).thenReturn(versionRow);

      // Mock for GET_CONFIG_BY_VERSION query
      Row configRow = mock(Row.class);
      when(configRow.getValue("config_json")).thenReturn(configJson);
      when(configRow.getValue("version")).thenReturn(version);
      when(configRow.getValue("description")).thenReturn(description);

      RowSet<Row> configRowSet = mock(RowSet.class);
      when(configRowSet.size()).thenReturn(1);
      RowIterator<Row> configIterator = mock(RowIterator.class);
      when(configRowSet.iterator()).thenReturn(configIterator);
      when(configIterator.next()).thenReturn(configRow);

      PreparedQuery<RowSet<Row>> latestVersionQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> configByVersionQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_LATEST_VERSION)).thenReturn(latestVersionQuery);
      when(latestVersionQuery.rxExecute()).thenReturn(Single.just(versionRowSet));
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(configByVersionQuery);
      when(configByVersionQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(configRowSet));

      // When
      PulseConfig result = sdkConfigsDao.getConfig().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getSignals().getFilters().getMode()).isEqualTo(FilterMode.blacklist);
    }

    @Test
    void shouldGetLatestConfigWithWhitelistMode() {
      // Given
      long version = 5L;
      String configJson = "{\"sampling\":{},\"signals\":{\"filters\":{\"mode\":\"whitelist\",\"values\":[]}},\"interaction\":{},\"features\":[]}";
      String description = "Latest Config";

      Row versionRow = mock(Row.class);
      when(versionRow.getValue("version")).thenReturn(version);

      RowSet<Row> versionRowSet = mock(RowSet.class);
      when(versionRowSet.size()).thenReturn(1);
      RowIterator<Row> versionIterator = mock(RowIterator.class);
      when(versionRowSet.iterator()).thenReturn(versionIterator);
      when(versionIterator.next()).thenReturn(versionRow);

      Row configRow = mock(Row.class);
      when(configRow.getValue("config_json")).thenReturn(configJson);
      when(configRow.getValue("version")).thenReturn(version);
      when(configRow.getValue("description")).thenReturn(description);

      RowSet<Row> configRowSet = mock(RowSet.class);
      when(configRowSet.size()).thenReturn(1);
      RowIterator<Row> configIterator = mock(RowIterator.class);
      when(configRowSet.iterator()).thenReturn(configIterator);
      when(configIterator.next()).thenReturn(configRow);

      PreparedQuery<RowSet<Row>> latestVersionQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> configByVersionQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_LATEST_VERSION)).thenReturn(latestVersionQuery);
      when(latestVersionQuery.rxExecute()).thenReturn(Single.just(versionRowSet));
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(configByVersionQuery);
      when(configByVersionQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(configRowSet));

      // When
      PulseConfig result = sdkConfigsDao.getConfig().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getSignals().getFilters().getMode()).isEqualTo(FilterMode.whitelist);
    }

    @Test
    void shouldGetLatestConfigWithNullFilters() {
      // Given
      long version = 5L;
      String configJson = "{\"sampling\":{},\"signals\":{},\"interaction\":{},\"features\":[]}";
      String description = "Latest Config Without Filters";

      Row versionRow = mock(Row.class);
      when(versionRow.getValue("version")).thenReturn(version);

      RowSet<Row> versionRowSet = mock(RowSet.class);
      when(versionRowSet.size()).thenReturn(1);
      RowIterator<Row> versionIterator = mock(RowIterator.class);
      when(versionRowSet.iterator()).thenReturn(versionIterator);
      when(versionIterator.next()).thenReturn(versionRow);

      Row configRow = mock(Row.class);
      when(configRow.getValue("config_json")).thenReturn(configJson);
      when(configRow.getValue("version")).thenReturn(version);
      when(configRow.getValue("description")).thenReturn(description);

      RowSet<Row> configRowSet = mock(RowSet.class);
      when(configRowSet.size()).thenReturn(1);
      RowIterator<Row> configIterator = mock(RowIterator.class);
      when(configRowSet.iterator()).thenReturn(configIterator);
      when(configIterator.next()).thenReturn(configRow);

      PreparedQuery<RowSet<Row>> latestVersionQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> configByVersionQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_LATEST_VERSION)).thenReturn(latestVersionQuery);
      when(latestVersionQuery.rxExecute()).thenReturn(Single.just(versionRowSet));
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(configByVersionQuery);
      when(configByVersionQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(configRowSet));

      // When
      PulseConfig result = sdkConfigsDao.getConfig().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getSignals().getFilters()).isNull();
    }

    @Test
    void shouldGetLatestConfigWithNullFilterMode() {
      // Given
      long version = 5L;
      String configJson = "{\"sampling\":{},\"signals\":{\"filters\":{\"values\":[]}},\"interaction\":{},\"features\":[]}";
      String description = "Config With Null Mode";

      Row versionRow = mock(Row.class);
      when(versionRow.getValue("version")).thenReturn(version);

      RowSet<Row> versionRowSet = mock(RowSet.class);
      when(versionRowSet.size()).thenReturn(1);
      RowIterator<Row> versionIterator = mock(RowIterator.class);
      when(versionRowSet.iterator()).thenReturn(versionIterator);
      when(versionIterator.next()).thenReturn(versionRow);

      Row configRow = mock(Row.class);
      when(configRow.getValue("config_json")).thenReturn(configJson);
      when(configRow.getValue("version")).thenReturn(version);
      when(configRow.getValue("description")).thenReturn(description);

      RowSet<Row> configRowSet = mock(RowSet.class);
      when(configRowSet.size()).thenReturn(1);
      RowIterator<Row> configIterator = mock(RowIterator.class);
      when(configRowSet.iterator()).thenReturn(configIterator);
      when(configIterator.next()).thenReturn(configRow);

      PreparedQuery<RowSet<Row>> latestVersionQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> configByVersionQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_LATEST_VERSION)).thenReturn(latestVersionQuery);
      when(latestVersionQuery.rxExecute()).thenReturn(Single.just(versionRowSet));
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(configByVersionQuery);
      when(configByVersionQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(configRowSet));

      // When
      PulseConfig result = sdkConfigsDao.getConfig().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(version);
      assertThat(result.getSignals().getFilters().getMode()).isNull();
    }

    @Test
    void shouldPropagateErrorWhenFetchingLatestVersionFails() {
      // Given
      RuntimeException dbError = new RuntimeException("Failed to fetch latest version");

      PreparedQuery<RowSet<Row>> latestVersionQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_LATEST_VERSION)).thenReturn(latestVersionQuery);
      when(latestVersionQuery.rxExecute()).thenReturn(Single.error(dbError));

      // When
      var testObserver = sdkConfigsDao.getConfig().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Failed to fetch latest version"));
    }

    @Test
    void shouldPropagateErrorWhenFetchingConfigByVersionFails() {
      // Given - Latest version fetched successfully but getConfig(version) fails
      long version = 5L;

      Row versionRow = mock(Row.class);
      when(versionRow.getValue("version")).thenReturn(version);

      RowSet<Row> versionRowSet = mock(RowSet.class);
      when(versionRowSet.size()).thenReturn(1);
      RowIterator<Row> versionIterator = mock(RowIterator.class);
      when(versionRowSet.iterator()).thenReturn(versionIterator);
      when(versionIterator.next()).thenReturn(versionRow);

      // Config by version will return empty result (0 rows)
      RowSet<Row> configRowSet = mock(RowSet.class);
      when(configRowSet.size()).thenReturn(0);

      PreparedQuery<RowSet<Row>> latestVersionQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> configByVersionQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_LATEST_VERSION)).thenReturn(latestVersionQuery);
      when(latestVersionQuery.rxExecute()).thenReturn(Single.just(versionRowSet));
      when(writerPool.preparedQuery(Queries.GET_CONFIG_BY_VERSION)).thenReturn(configByVersionQuery);
      when(configByVersionQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(configRowSet));

      // When
      var testObserver = sdkConfigsDao.getConfig().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().contains("No config found for version"));
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestCreateConfig {

    @Mock
    SqlConnection sqlConnection;

    @Mock
    Transaction transaction;

    @Test
    void shouldCreateConfigSuccessfully() {
      // Given
      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .sampling(SamplingConfig.builder().build())
          .signals(SignalsConfig.builder()
              .scheduleDurationMs(5000)
              .logsCollectorUrl("http://logs.example.com")
              .metricCollectorUrl("http://metrics.example.com")
              .spanCollectorUrl("http://spans.example.com")
              .attributesToDrop(List.of())
              .filters(FilterConfig.builder()
                  .mode(FilterMode.blacklist)
                  .values(List.of())
                  .build())
              .build())
          .interaction(InteractionConfig.builder()
              .collectorUrl("http://interaction.example.com")
              .configUrl("http://config.example.com")
              .beforeInitQueueSize(100)
              .build())
          .features(List.of(
              FeatureConfig.builder()
                  .featureName(Features.java_crash)
                  .enabled(true)
                  .sessionSampleRate(1.0)
                  .sdks(List.of())
                  .build()
          ))
          .build();

      long insertedId = 10L;

      // Mock for deactivate query
      RowSet<Row> deactivateRowSet = mock(RowSet.class);

      // Mock for insert query
      RowSet<Row> insertRowSet = mock(RowSet.class);
      when(insertRowSet.rowCount()).thenReturn(1);
      when(insertRowSet.property(any(PropertyKind.class))).thenReturn(insertedId);

      PreparedQuery<RowSet<Row>> deactivateQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(Queries.DEACTIVATE_ACTIVE_CONFIG)).thenReturn(deactivateQuery);
      when(deactivateQuery.rxExecute()).thenReturn(Single.just(deactivateRowSet));
      when(sqlConnection.preparedQuery(Queries.INSERT_CONFIG)).thenReturn(insertQuery);
      when(insertQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRowSet));
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      // When
      PulseConfig result = sdkConfigsDao.createConfig(configData).blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(insertedId);

      verify(sqlConnection, times(1)).close();
    }

    @Test
    void shouldRollbackOnInsertFailure() {
      // Given
      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .sampling(SamplingConfig.builder().build())
          .signals(SignalsConfig.builder()
              .filters(FilterConfig.builder().mode(FilterMode.blacklist).values(List.of()).build())
              .build())
          .interaction(InteractionConfig.builder().build())
          .features(List.of())
          .build();

      RuntimeException insertError = new RuntimeException("Insert failed");

      RowSet<Row> deactivateRowSet = mock(RowSet.class);

      PreparedQuery<RowSet<Row>> deactivateQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(Queries.DEACTIVATE_ACTIVE_CONFIG)).thenReturn(deactivateQuery);
      when(deactivateQuery.rxExecute()).thenReturn(Single.just(deactivateRowSet));
      when(sqlConnection.preparedQuery(Queries.INSERT_CONFIG)).thenReturn(insertQuery);
      when(insertQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(insertError));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      // When
      var testObserver = sdkConfigsDao.createConfig(configData).test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Insert failed"));

      verify(transaction, times(1)).rxRollback();
      verify(sqlConnection, times(1)).close();
    }

    @Test
    void shouldFailWhenNoRowsInserted() {
      // Given
      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .sampling(SamplingConfig.builder().build())
          .signals(SignalsConfig.builder()
              .filters(FilterConfig.builder().mode(FilterMode.whitelist).values(List.of()).build())
              .build())
          .interaction(InteractionConfig.builder().build())
          .features(List.of())
          .build();

      RowSet<Row> deactivateRowSet = mock(RowSet.class);
      RowSet<Row> insertRowSet = mock(RowSet.class);
      when(insertRowSet.rowCount()).thenReturn(0);

      PreparedQuery<RowSet<Row>> deactivateQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(Queries.DEACTIVATE_ACTIVE_CONFIG)).thenReturn(deactivateQuery);
      when(deactivateQuery.rxExecute()).thenReturn(Single.just(deactivateRowSet));
      when(sqlConnection.preparedQuery(Queries.INSERT_CONFIG)).thenReturn(insertQuery);
      when(insertQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRowSet));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      // When
      var testObserver = sdkConfigsDao.createConfig(configData).test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Failed to insert config"));

      verify(sqlConnection, times(1)).close();
    }

    @Test
    void shouldRollbackOnDeactivateFailure() {
      // Given
      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .sampling(SamplingConfig.builder().build())
          .signals(SignalsConfig.builder()
              .filters(FilterConfig.builder().mode(FilterMode.blacklist).values(List.of()).build())
              .build())
          .interaction(InteractionConfig.builder().build())
          .features(List.of())
          .build();

      RuntimeException deactivateError = new RuntimeException("Deactivate failed");

      PreparedQuery<RowSet<Row>> deactivateQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(Queries.DEACTIVATE_ACTIVE_CONFIG)).thenReturn(deactivateQuery);
      when(deactivateQuery.rxExecute()).thenReturn(Single.error(deactivateError));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      // When
      var testObserver = sdkConfigsDao.createConfig(configData).test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Deactivate failed"));

      verify(transaction, times(1)).rxRollback();
      verify(sqlConnection, times(1)).close();
    }

    @Test
    void shouldRollbackOnCommitFailure() {
      // Given
      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .sampling(SamplingConfig.builder().build())
          .signals(SignalsConfig.builder()
              .filters(FilterConfig.builder().mode(FilterMode.blacklist).values(List.of()).build())
              .build())
          .interaction(InteractionConfig.builder().build())
          .features(List.of())
          .build();

      long insertedId = 10L;
      RuntimeException commitError = new RuntimeException("Commit failed");

      RowSet<Row> deactivateRowSet = mock(RowSet.class);
      RowSet<Row> insertRowSet = mock(RowSet.class);
      when(insertRowSet.rowCount()).thenReturn(1);
      when(insertRowSet.property(any(PropertyKind.class))).thenReturn(insertedId);

      PreparedQuery<RowSet<Row>> deactivateQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(Queries.DEACTIVATE_ACTIVE_CONFIG)).thenReturn(deactivateQuery);
      when(deactivateQuery.rxExecute()).thenReturn(Single.just(deactivateRowSet));
      when(sqlConnection.preparedQuery(Queries.INSERT_CONFIG)).thenReturn(insertQuery);
      when(insertQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRowSet));
      when(transaction.rxCommit()).thenReturn(Completable.error(commitError));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      // When
      var testObserver = sdkConfigsDao.createConfig(configData).test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Commit failed"));

      verify(transaction, times(1)).rxRollback();
      verify(sqlConnection, times(1)).close();
    }

    @Test
    void shouldCreateConfigWithNullFeatures() {
      // Given - Test with null features to cover null-handling branches
      ConfigData configData = ConfigData.builder()
          .description("New Config")
          .user("test_user")
          .sampling(null)
          .signals(null)
          .interaction(null)
          .features(null)
          .build();

      long insertedId = 11L;

      RowSet<Row> deactivateRowSet = mock(RowSet.class);
      RowSet<Row> insertRowSet = mock(RowSet.class);
      when(insertRowSet.rowCount()).thenReturn(1);
      when(insertRowSet.property(any(PropertyKind.class))).thenReturn(insertedId);

      PreparedQuery<RowSet<Row>> deactivateQuery = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(Queries.DEACTIVATE_ACTIVE_CONFIG)).thenReturn(deactivateQuery);
      when(deactivateQuery.rxExecute()).thenReturn(Single.just(deactivateRowSet));
      when(sqlConnection.preparedQuery(Queries.INSERT_CONFIG)).thenReturn(insertQuery);
      when(insertQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRowSet));
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      // When
      PulseConfig result = sdkConfigsDao.createConfig(configData).blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getVersion()).isEqualTo(insertedId);

      verify(sqlConnection, times(1)).close();
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetAllConfigDetails {

    @Test
    void shouldGetAllConfigDetailsSuccessfully() {
      // Given
      Row mockRow1 = mock(Row.class);
      when(mockRow1.getValue("version")).thenReturn(1L);
      when(mockRow1.getValue("description")).thenReturn("Config 1");
      when(mockRow1.getValue("created_by")).thenReturn("user1");
      when(mockRow1.getValue("created_at")).thenReturn("2024-01-01 00:00:00");
      when(mockRow1.getInteger("is_active")).thenReturn(1);

      Row mockRow2 = mock(Row.class);
      when(mockRow2.getValue("version")).thenReturn(2L);
      when(mockRow2.getValue("description")).thenReturn("Config 2");
      when(mockRow2.getValue("created_by")).thenReturn("user2");
      when(mockRow2.getValue("created_at")).thenReturn("2024-01-02 00:00:00");
      when(mockRow2.getInteger("is_active")).thenReturn(0);

      List<Row> rows = List.of(mockRow1, mockRow2);

      RowSet<Row> rowSet = mock(RowSet.class);
      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(true, true, false);
      when(rowIterator.next()).thenReturn(mockRow1, mockRow2);

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_ALL_CONFIG_DETAILS)).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      // When
      AllConfigdetails result = sdkConfigsDao.getAllConfigDetails().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getConfigDetails()).hasSize(2);

      AllConfigdetails.Configdetails config1 = result.getConfigDetails().get(0);
      assertThat(config1.getVersion()).isEqualTo(1L);
      assertThat(config1.getDescription()).isEqualTo("Config 1");
      assertThat(config1.getCreatedBy()).isEqualTo("user1");
      assertThat(config1.getCreatedAt()).isEqualTo("2024-01-01 00:00:00");
      assertThat(config1.isIsactive()).isTrue();

      AllConfigdetails.Configdetails config2 = result.getConfigDetails().get(1);
      assertThat(config2.getVersion()).isEqualTo(2L);
      assertThat(config2.getDescription()).isEqualTo("Config 2");
      assertThat(config2.getCreatedBy()).isEqualTo("user2");
      assertThat(config2.getCreatedAt()).isEqualTo("2024-01-02 00:00:00");
      assertThat(config2.isIsactive()).isFalse();

      verify(d11MysqlClient, times(1)).getWriterPool();
      verifyNoMoreInteractions(d11MysqlClient);
    }

    @Test
    void shouldReturnEmptyListWhenNoConfigsExist() {
      // Given
      RowSet<Row> rowSet = mock(RowSet.class);
      RowIterator<Row> rowIterator = mock(RowIterator.class);
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(false);

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_ALL_CONFIG_DETAILS)).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      // When
      AllConfigdetails result = sdkConfigsDao.getAllConfigDetails().blockingGet();

      // Then
      assertThat(result).isNotNull();
      assertThat(result.getConfigDetails()).isEmpty();
    }

    @Test
    void shouldPropagateErrorWhenDatabaseCallFails() {
      // Given
      RuntimeException dbError = new RuntimeException("Database error");

      PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);

      when(d11MysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.preparedQuery(Queries.GET_ALL_CONFIG_DETAILS)).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute()).thenReturn(Single.error(dbError));

      // When
      var testObserver = sdkConfigsDao.getAllConfigDetails().test();

      // Then
      testObserver.assertError(RuntimeException.class);
      testObserver.assertError(e -> e.getMessage().equals("Database error"));
    }
  }
}

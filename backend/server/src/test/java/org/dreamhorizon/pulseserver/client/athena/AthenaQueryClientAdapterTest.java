package org.dreamhorizon.pulseserver.client.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.athena.models.ResultSetWithToken;
import org.dreamhorizon.pulseserver.client.query.models.QueryExecutionInfo;
import org.dreamhorizon.pulseserver.client.query.models.QueryResultSet;
import org.dreamhorizon.pulseserver.client.query.models.QueryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatistics;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.ResultSetMetadata;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AthenaQueryClientAdapterTest {

  @Mock
  AthenaClient athenaClient;

  @Mock
  AthenaResultConverter resultConverter;

  AthenaQueryClientAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AthenaQueryClientAdapter(athenaClient, resultConverter);
  }

  @Nested
  class TestSubmitQuery {

    @Test
    void shouldSubmitQuery() {
      String query = "SELECT * FROM table";
      List<String> parameters = Collections.emptyList();
      String executionId = "exec-123";

      when(athenaClient.submitQuery(query, parameters)).thenReturn(Single.just(executionId));

      String result = adapter.submitQuery(query, parameters).blockingGet();

      assertThat(result).isEqualTo(executionId);
      verify(athenaClient).submitQuery(query, parameters);
    }
  }

  @Nested
  class TestGetQueryStatus {

    @Test
    void shouldMapQueuedStatus() {
      when(athenaClient.getQueryStatus("exec-123"))
          .thenReturn(Single.just(QueryExecutionState.QUEUED));

      QueryStatus result = adapter.getQueryStatus("exec-123").blockingGet();

      assertThat(result).isEqualTo(QueryStatus.QUEUED);
    }

    @Test
    void shouldMapRunningStatus() {
      when(athenaClient.getQueryStatus("exec-123"))
          .thenReturn(Single.just(QueryExecutionState.RUNNING));

      QueryStatus result = adapter.getQueryStatus("exec-123").blockingGet();

      assertThat(result).isEqualTo(QueryStatus.RUNNING);
    }

    @Test
    void shouldMapSucceededStatus() {
      when(athenaClient.getQueryStatus("exec-123"))
          .thenReturn(Single.just(QueryExecutionState.SUCCEEDED));

      QueryStatus result = adapter.getQueryStatus("exec-123").blockingGet();

      assertThat(result).isEqualTo(QueryStatus.SUCCEEDED);
    }

    @Test
    void shouldMapFailedStatus() {
      when(athenaClient.getQueryStatus("exec-123"))
          .thenReturn(Single.just(QueryExecutionState.FAILED));

      QueryStatus result = adapter.getQueryStatus("exec-123").blockingGet();

      assertThat(result).isEqualTo(QueryStatus.FAILED);
    }

    @Test
    void shouldMapCancelledStatus() {
      when(athenaClient.getQueryStatus("exec-123"))
          .thenReturn(Single.just(QueryExecutionState.CANCELLED));

      QueryStatus result = adapter.getQueryStatus("exec-123").blockingGet();

      assertThat(result).isEqualTo(QueryStatus.CANCELLED);
    }
  }

  @Nested
  class TestWaitForQueryCompletion {

    @Test
    void shouldWaitForCompletion() {
      when(athenaClient.waitForQueryCompletion("exec-123"))
          .thenReturn(Single.just(QueryExecutionState.SUCCEEDED));

      QueryStatus result = adapter.waitForQueryCompletion("exec-123").blockingGet();

      assertThat(result).isEqualTo(QueryStatus.SUCCEEDED);
      verify(athenaClient).waitForQueryCompletion("exec-123");
    }
  }

  @Nested
  class TestGetQueryResults {

    @Test
    void shouldGetQueryResults() {
      String executionId = "exec-123";
      String nextToken = "token-123";

      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .build();

      io.vertx.core.json.JsonArray jsonArray = new io.vertx.core.json.JsonArray();
      jsonArray.add(new io.vertx.core.json.JsonObject().put("col1", "value1"));

      ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, nextToken);

      when(athenaClient.getQueryResults(executionId, 100, null))
          .thenReturn(Single.just(resultSetWithToken));
      when(resultConverter.convertToJsonArray(resultSet)).thenReturn(jsonArray);

      QueryResultSet result = adapter.getQueryResults(executionId, 100, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getNextToken()).isEqualTo(nextToken);
      assertThat(result.getResultData()).isEqualTo(jsonArray);
      verify(resultConverter).convertToJsonArray(resultSet);
    }
  }

  @Nested
  class TestGetQueryExecution {

    @Test
    void shouldGetQueryExecution() {
      String executionId = "exec-123";
      String resultLocation = "s3://bucket/path";
      Long dataScannedBytes = 1000L;

      QueryExecutionStatistics stats = QueryExecutionStatistics.builder()
          .dataScannedInBytes(dataScannedBytes)
          .build();
      ResultConfiguration resultConfig = ResultConfiguration.builder()
          .outputLocation(resultLocation)
          .build();
      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .state(QueryExecutionState.SUCCEEDED)
          .stateChangeReason("Query succeeded")
          .build();
      QueryExecution execution = QueryExecution.builder()
          .queryExecutionId(executionId)
          .status(status)
          .statistics(s -> s.dataScannedInBytes(dataScannedBytes))
          .resultConfiguration(r -> r.outputLocation(resultLocation))
          .build();

      when(athenaClient.getQueryExecution(executionId))
          .thenReturn(Single.just(execution));

      QueryExecutionInfo result = adapter.getQueryExecution(executionId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getQueryExecutionId()).isEqualTo(executionId);
      assertThat(result.getStatus()).isEqualTo(QueryStatus.SUCCEEDED);
      assertThat(result.getResultLocation()).isEqualTo(resultLocation);
      assertThat(result.getDataScannedInBytes()).isEqualTo(dataScannedBytes);
      assertThat(result.getStateChangeReason()).isEqualTo("Query succeeded");
    }

    @Test
    void shouldHandleNullResultConfiguration() {
      String executionId = "exec-123";

      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .state(QueryExecutionState.RUNNING)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .queryExecutionId(executionId)
          .status(status)
          .build();

      when(athenaClient.getQueryExecution(executionId))
          .thenReturn(Single.just(execution));

      QueryExecutionInfo result = adapter.getQueryExecution(executionId).blockingGet();

      assertThat(result.getResultLocation()).isNull();
    }

    @Test
    void shouldHandleNullStatistics() {
      String executionId = "exec-123";

      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .state(QueryExecutionState.RUNNING)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .queryExecutionId(executionId)
          .status(status)
          .build();

      when(athenaClient.getQueryExecution(executionId))
          .thenReturn(Single.just(execution));

      QueryExecutionInfo result = adapter.getQueryExecution(executionId).blockingGet();

      assertThat(result.getDataScannedInBytes()).isNull();
    }
  }
}


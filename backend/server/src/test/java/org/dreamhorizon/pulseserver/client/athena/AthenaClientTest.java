package org.dreamhorizon.pulseserver.client.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.client.athena.models.ResultSetWithToken;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.athena.AthenaAsyncClient;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.ResultSetMetadata;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AthenaClientTest {

  @Mock
  AthenaConfig athenaConfig;

  @Mock
  AthenaAsyncClient athenaAsyncClient;

  AthenaClient athenaClient;

  @BeforeEach
  void setUp() {
    when(athenaConfig.getDatabase()).thenReturn("test_db");
    when(athenaConfig.getOutputLocation()).thenReturn("s3://test-bucket/");
    athenaClient = new AthenaClient(athenaConfig, athenaAsyncClient);
  }

  @Nested
  class TestSubmitQuery {

    @Test
    void shouldSubmitQuerySuccessfully() {
      String query = "SELECT * FROM table";
      String executionId = "exec-123";

      StartQueryExecutionResponse response = StartQueryExecutionResponse.builder()
          .queryExecutionId(executionId)
          .build();

      CompletableFuture<StartQueryExecutionResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class))).thenReturn(future);

      String result = athenaClient.submitQuery(query, Collections.emptyList()).blockingGet();

      assertThat(result).isEqualTo(executionId);
      verify(athenaAsyncClient).startQueryExecution(any(StartQueryExecutionRequest.class));
    }

    @Test
    void shouldSubmitQueryWithParameters() {
      String query = "SELECT * FROM table WHERE id = ?";
      String executionId = "exec-123";
      java.util.List<String> parameters = Arrays.asList("123");

      StartQueryExecutionResponse response = StartQueryExecutionResponse.builder()
          .queryExecutionId(executionId)
          .build();

      CompletableFuture<StartQueryExecutionResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class))).thenReturn(future);

      String result = athenaClient.submitQuery(query, parameters).blockingGet();

      assertThat(result).isEqualTo(executionId);
    }

    @Test
    void shouldHandleSubmitQueryError() {
      String query = "SELECT * FROM table";
      RuntimeException error = new RuntimeException("AWS error");

      CompletableFuture<StartQueryExecutionResponse> future = new CompletableFuture<>();
      future.completeExceptionally(error);
      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class))).thenReturn(future);

      var testObserver = athenaClient.submitQuery(query, Collections.emptyList()).test();
      testObserver.assertError(RuntimeException.class);
    }

    @Test
    void shouldHandleNullResponse() {
      String query = "SELECT * FROM table";

      CompletableFuture<StartQueryExecutionResponse> future = CompletableFuture.completedFuture(null);
      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class))).thenReturn(future);

      var testObserver = athenaClient.submitQuery(query, Collections.emptyList()).test();
      testObserver.assertError(RuntimeException.class);
    }

    @Test
    void shouldHandleEmptyExecutionId() {
      String query = "SELECT * FROM table";

      StartQueryExecutionResponse response = StartQueryExecutionResponse.builder()
          .queryExecutionId("")
          .build();

      CompletableFuture<StartQueryExecutionResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class))).thenReturn(future);

      var testObserver = athenaClient.submitQuery(query, Collections.emptyList()).test();
      testObserver.assertError(RuntimeException.class);
    }
  }

  @Nested
  class TestGetQueryStatus {

    @Test
    void shouldGetQueryStatus() {
      String executionId = "exec-123";

      QueryExecution queryExecution = QueryExecution.builder()
          .queryExecutionId(executionId)
          .status(QueryExecutionStatus.builder()
              .state(QueryExecutionState.RUNNING)
              .build())
          .build();

      GetQueryExecutionResponse response = GetQueryExecutionResponse.builder()
          .queryExecution(queryExecution)
          .build();

      CompletableFuture<GetQueryExecutionResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class))).thenReturn(future);

      QueryExecutionState result = athenaClient.getQueryStatus(executionId).blockingGet();

      assertThat(result).isEqualTo(QueryExecutionState.RUNNING);
    }

    @Test
    void shouldHandleNullResponse() {
      String executionId = "exec-123";

      CompletableFuture<GetQueryExecutionResponse> future = CompletableFuture.completedFuture(null);
      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class))).thenReturn(future);

      var testObserver = athenaClient.getQueryStatus(executionId).test();
      testObserver.assertError(RuntimeException.class);
    }
  }

  @Nested
  class TestGetQueryResults {

    @Test
    void shouldGetQueryResults() {
      String executionId = "exec-123";

      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();

      Row headerRow = Row.builder()
          .data(Arrays.asList(Datum.builder().varCharValue("col1").build()))
          .build();
      Row dataRow = Row.builder()
          .data(Arrays.asList(Datum.builder().varCharValue("value1").build()))
          .build();

      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Arrays.asList(headerRow, dataRow))
          .build();

      GetQueryResultsResponse response = GetQueryResultsResponse.builder()
          .resultSet(resultSet)
          .nextToken(null)
          .build();

      CompletableFuture<GetQueryResultsResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class))).thenReturn(future);

      ResultSetWithToken result = athenaClient.getQueryResults(executionId, 100, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getResultSet()).isNotNull();
    }

    @Test
    void shouldGetQueryResultsWithMaxResults() {
      String executionId = "exec-123";

      ResultSet resultSet = ResultSet.builder().build();
      GetQueryResultsResponse response = GetQueryResultsResponse.builder()
          .resultSet(resultSet)
          .build();

      CompletableFuture<GetQueryResultsResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class))).thenReturn(future);

      ResultSetWithToken result = athenaClient.getQueryResults(executionId, 50, null).blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectInvalidMaxResults() {
      String executionId = "exec-123";

      var testObserver = athenaClient.getQueryResults(executionId, 0, null).test();
      testObserver.assertError(IllegalArgumentException.class);

      testObserver = athenaClient.getQueryResults(executionId, Constants.MAX_QUERY_RESULTS + 1, null).test();
      testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    void shouldGetQueryResultsWithNextToken() {
      String executionId = "exec-123";
      String nextToken = "token-123";

      ResultSet resultSet = ResultSet.builder().build();
      GetQueryResultsResponse response = GetQueryResultsResponse.builder()
          .resultSet(resultSet)
          .nextToken("next-token")
          .build();

      CompletableFuture<GetQueryResultsResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class))).thenReturn(future);

      ResultSetWithToken result = athenaClient.getQueryResults(executionId, 100, nextToken).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getNextToken()).isEqualTo("next-token");
    }

    @Test
    void shouldHandleGetQueryResultsError() {
      String executionId = "exec-123";
      RuntimeException error = new RuntimeException("AWS error");

      CompletableFuture<GetQueryResultsResponse> future = new CompletableFuture<>();
      future.completeExceptionally(error);
      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class))).thenReturn(future);

      var testObserver = athenaClient.getQueryResults(executionId, 100, null).test();
      testObserver.assertError(RuntimeException.class);
    }
  }

  @Nested
  class TestGetQueryExecution {

    @Test
    void shouldGetQueryExecution() {
      String executionId = "exec-123";

      QueryExecution queryExecution = QueryExecution.builder()
          .queryExecutionId(executionId)
          .build();

      GetQueryExecutionResponse response = GetQueryExecutionResponse.builder()
          .queryExecution(queryExecution)
          .build();

      CompletableFuture<GetQueryExecutionResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class))).thenReturn(future);

      QueryExecution result = athenaClient.getQueryExecution(executionId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.queryExecutionId()).isEqualTo(executionId);
    }

    @Test
    void shouldHandleNullQueryExecution() {
      String executionId = "exec-123";

      GetQueryExecutionResponse response = GetQueryExecutionResponse.builder()
          .build();

      CompletableFuture<GetQueryExecutionResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class))).thenReturn(future);

      var testObserver = athenaClient.getQueryExecution(executionId).test();
      testObserver.assertError(RuntimeException.class);
    }
  }

  @Nested
  class TestWaitForQueryCompletion {

    @Test
    void shouldWaitForQueryCompletion() {
      String executionId = "exec-123";

      QueryExecution queryExecution = QueryExecution.builder()
          .queryExecutionId(executionId)
          .status(QueryExecutionStatus.builder()
              .state(QueryExecutionState.SUCCEEDED)
              .build())
          .build();

      GetQueryExecutionResponse response = GetQueryExecutionResponse.builder()
          .queryExecution(queryExecution)
          .build();

      CompletableFuture<GetQueryExecutionResponse> future = CompletableFuture.completedFuture(response);
      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class))).thenReturn(future);

      QueryExecutionState result = athenaClient.waitForQueryCompletion(executionId).blockingGet();

      assertThat(result).isEqualTo(QueryExecutionState.SUCCEEDED);
    }
  }
}


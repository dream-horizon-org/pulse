package org.dreamhorizon.pulseserver.client.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.client.athena.models.ResultSetWithToken;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.athena.AthenaAsyncClient;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;

@ExtendWith(MockitoExtension.class)
class AthenaClientTest {

  @Mock
  AthenaAsyncClient athenaAsyncClient;

  @Mock
  AthenaConfig athenaConfig;

  AthenaClient athenaClient;

  @BeforeEach
  void setUp() {
    when(athenaConfig.getDatabase()).thenReturn("pulse_athena_db");
    when(athenaConfig.getOutputLocation()).thenReturn("s3://puls-otel-config/");
    when(athenaConfig.getAthenaRegion()).thenReturn("ap-south-1");
    
    athenaClient = new AthenaClient(athenaConfig, athenaAsyncClient);
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestSubmitQuery {

    @Test
    void shouldSubmitQuerySuccessfully() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025";
      List<String> parameters = Collections.emptyList();
      String queryExecutionId = "abc-123-def-456";

      StartQueryExecutionResponse mockResponse = StartQueryExecutionResponse.builder()
          .queryExecutionId(queryExecutionId)
          .build();

      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      String result = athenaClient.submitQuery(query, parameters).blockingGet();

      assertThat(result).isEqualTo(queryExecutionId);

      ArgumentCaptor<StartQueryExecutionRequest> captor =
          ArgumentCaptor.forClass(StartQueryExecutionRequest.class);
      verify(athenaAsyncClient).startQueryExecution(captor.capture());

      StartQueryExecutionRequest request = captor.getValue();
      assertThat(request.queryString()).isEqualTo(query);
      assertThat(request.queryExecutionContext().database()).isEqualTo("pulse_athena_db");
      assertThat(request.resultConfiguration().outputLocation()).isEqualTo("s3://puls-otel-config/");
    }

    @Test
    void shouldSubmitQueryWithParameters() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = ?";
      List<String> parameters = Arrays.asList("2025");

      StartQueryExecutionResponse mockResponse = StartQueryExecutionResponse.builder()
          .queryExecutionId("exec-123")
          .build();

      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      String result = athenaClient.submitQuery(query, parameters).blockingGet();

      assertThat(result).isEqualTo("exec-123");

      ArgumentCaptor<StartQueryExecutionRequest> captor =
          ArgumentCaptor.forClass(StartQueryExecutionRequest.class);
      verify(athenaAsyncClient).startQueryExecution(captor.capture());

      StartQueryExecutionRequest request = captor.getValue();
      assertThat(request.executionParameters()).containsExactly("2025");
    }

    @Test
    void shouldPropagateErrorWhenSubmissionFails() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      List<String> parameters = Collections.emptyList();

      RuntimeException athenaError = new RuntimeException("Athena API error");
      CompletableFuture<StartQueryExecutionResponse> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(athenaError);

      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
          .thenReturn(failedFuture);

      var testObserver = athenaClient.submitQuery(query, parameters).test();

      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleNullResponse() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      List<String> parameters = Collections.emptyList();

      CompletableFuture<StartQueryExecutionResponse> nullFuture = new CompletableFuture<>();
      nullFuture.complete(null);

      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
          .thenReturn(nullFuture);

      var testObserver = athenaClient.submitQuery(query, parameters).test();

      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleEmptyQueryExecutionId() {
      String query = "SELECT * FROM pulse_athena_db.otel_data";
      List<String> parameters = Collections.emptyList();

      StartQueryExecutionResponse mockResponse = StartQueryExecutionResponse.builder()
          .queryExecutionId("")
          .build();

      when(athenaAsyncClient.startQueryExecution(any(StartQueryExecutionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      var testObserver = athenaClient.submitQuery(query, parameters).test();

      testObserver.assertError(Throwable.class);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetQueryStatus {

    @Test
    void shouldGetQueryStatusSuccessfully() {
      String queryExecutionId = "abc-123-def-456";
      QueryExecutionState state = QueryExecutionState.RUNNING;

      QueryExecution queryExecution = QueryExecution.builder()
          .status(QueryExecutionStatus.builder()
              .state(state)
              .build())
          .build();

      GetQueryExecutionResponse mockResponse = GetQueryExecutionResponse.builder()
          .queryExecution(queryExecution)
          .build();

      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      QueryExecutionState result = athenaClient.getQueryStatus(queryExecutionId).blockingGet();

      assertThat(result).isEqualTo(state);

      ArgumentCaptor<GetQueryExecutionRequest> captor =
          ArgumentCaptor.forClass(GetQueryExecutionRequest.class);
      verify(athenaAsyncClient).getQueryExecution(captor.capture());

      assertThat(captor.getValue().queryExecutionId()).isEqualTo(queryExecutionId);
    }

    @Test
    void shouldPropagateErrorWhenStatusCheckFails() {
      String queryExecutionId = "abc-123-def-456";

      RuntimeException athenaError = new RuntimeException("Athena API error");
      CompletableFuture<GetQueryExecutionResponse> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(athenaError);

      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
          .thenReturn(failedFuture);

      var testObserver = athenaClient.getQueryStatus(queryExecutionId).test();

      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleNullResponse() {
      String queryExecutionId = "abc-123-def-456";

      CompletableFuture<GetQueryExecutionResponse> nullFuture = new CompletableFuture<>();
      nullFuture.complete(null);

      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
          .thenReturn(nullFuture);

      var testObserver = athenaClient.getQueryStatus(queryExecutionId).test();

      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleNullQueryExecution() {
      String queryExecutionId = "abc-123-def-456";

      GetQueryExecutionResponse mockResponse = GetQueryExecutionResponse.builder()
          .build();

      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      var testObserver = athenaClient.getQueryStatus(queryExecutionId).test();

      testObserver.assertError(Throwable.class);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetQueryResults {

    @Test
    void shouldGetQueryResultsSuccessfully() {
      String queryExecutionId = "abc-123-def-456";
      String nextToken = "token-123";

      ResultSet resultSet = ResultSet.builder().build();
      GetQueryResultsResponse mockResponse = GetQueryResultsResponse.builder()
          .resultSet(resultSet)
          .nextToken(nextToken)
          .build();

      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      ResultSetWithToken result = athenaClient.getQueryResults(queryExecutionId, 100, null)
          .blockingGet();

      assertThat(result.getResultSet()).isEqualTo(resultSet);
      assertThat(result.getNextToken()).isEqualTo(nextToken);

      ArgumentCaptor<GetQueryResultsRequest> captor =
          ArgumentCaptor.forClass(GetQueryResultsRequest.class);
      verify(athenaAsyncClient).getQueryResults(captor.capture());

      GetQueryResultsRequest request = captor.getValue();
      assertThat(request.queryExecutionId()).isEqualTo(queryExecutionId);
      assertThat(request.maxResults()).isEqualTo(100);
    }

    @Test
    void shouldGetQueryResultsWithNextToken() {
      String queryExecutionId = "abc-123-def-456";
      String nextToken = "token-456";

      ResultSet resultSet = ResultSet.builder().build();
      GetQueryResultsResponse mockResponse = GetQueryResultsResponse.builder()
          .resultSet(resultSet)
          .nextToken("next-token")
          .build();

      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      ResultSetWithToken result = athenaClient.getQueryResults(queryExecutionId, 50, nextToken)
          .blockingGet();

      assertThat(result.getResultSet()).isEqualTo(resultSet);

      ArgumentCaptor<GetQueryResultsRequest> captor =
          ArgumentCaptor.forClass(GetQueryResultsRequest.class);
      verify(athenaAsyncClient).getQueryResults(captor.capture());

      GetQueryResultsRequest request = captor.getValue();
      assertThat(request.nextToken()).isEqualTo(nextToken);
      assertThat(request.maxResults()).isEqualTo(50);
    }

    @Test
    void shouldGetQueryResultsWithoutPagination() {
      String queryExecutionId = "abc-123-def-456";

      ResultSet resultSet = ResultSet.builder().build();
      GetQueryResultsResponse mockResponse = GetQueryResultsResponse.builder()
          .resultSet(resultSet)
          .build();

      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      ResultSet result = athenaClient.getQueryResults(queryExecutionId).blockingGet();

      assertThat(result).isEqualTo(resultSet);
    }

    @Test
    void shouldRejectInvalidMaxResults() {
      String queryExecutionId = "abc-123-def-456";

      var testObserver = athenaClient.getQueryResults(queryExecutionId, 0, null).test();
      testObserver.assertError(IllegalArgumentException.class);

      testObserver = athenaClient.getQueryResults(queryExecutionId, 1001, null).test();
      testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    void shouldPropagateErrorWhenResultsFetchFails() {
      String queryExecutionId = "abc-123-def-456";

      RuntimeException athenaError = new RuntimeException("Athena API error");
      CompletableFuture<GetQueryResultsResponse> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(athenaError);

      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class)))
          .thenReturn(failedFuture);

      var testObserver = athenaClient.getQueryResults(queryExecutionId, 100, null).test();

      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleNullResultSet() {
      String queryExecutionId = "abc-123-def-456";

      GetQueryResultsResponse mockResponse = GetQueryResultsResponse.builder()
          .build();

      when(athenaAsyncClient.getQueryResults(any(GetQueryResultsRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      var testObserver = athenaClient.getQueryResults(queryExecutionId, 100, null).test();

      testObserver.assertError(Throwable.class);
    }
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  @MockitoSettings(strictness = Strictness.LENIENT)
  class TestGetQueryExecution {

    @Test
    void shouldGetQueryExecutionSuccessfully() {
      String queryExecutionId = "abc-123-def-456";

      QueryExecution queryExecution = QueryExecution.builder()
          .queryExecutionId(queryExecutionId)
          .build();

      GetQueryExecutionResponse mockResponse = GetQueryExecutionResponse.builder()
          .queryExecution(queryExecution)
          .build();

      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      QueryExecution result = athenaClient.getQueryExecution(queryExecutionId).blockingGet();

      assertThat(result).isEqualTo(queryExecution);
      assertThat(result.queryExecutionId()).isEqualTo(queryExecutionId);

      ArgumentCaptor<GetQueryExecutionRequest> captor =
          ArgumentCaptor.forClass(GetQueryExecutionRequest.class);
      verify(athenaAsyncClient).getQueryExecution(captor.capture());

      assertThat(captor.getValue().queryExecutionId()).isEqualTo(queryExecutionId);
    }

    @Test
    void shouldPropagateErrorWhenGetQueryExecutionFails() {
      String queryExecutionId = "abc-123-def-456";

      RuntimeException athenaError = new RuntimeException("Athena API error");
      CompletableFuture<GetQueryExecutionResponse> failedFuture = new CompletableFuture<>();
      failedFuture.completeExceptionally(athenaError);

      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
          .thenReturn(failedFuture);

      var testObserver = athenaClient.getQueryExecution(queryExecutionId).test();

      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleNullQueryExecutionInResponse() {
      String queryExecutionId = "abc-123-def-456";

      GetQueryExecutionResponse mockResponse = GetQueryExecutionResponse.builder()
          .build();

      when(athenaAsyncClient.getQueryExecution(any(GetQueryExecutionRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(mockResponse));

      var testObserver = athenaClient.getQueryExecution(queryExecutionId).test();

      testObserver.assertError(Throwable.class);
    }
  }
}


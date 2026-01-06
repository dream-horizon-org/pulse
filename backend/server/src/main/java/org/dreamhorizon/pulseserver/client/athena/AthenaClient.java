package org.dreamhorizon.pulseserver.client.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.athena.models.ResultSetWithToken;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import software.amazon.awssdk.services.athena.AthenaAsyncClient;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AthenaClient {
  private final AthenaConfig athenaConfig;
  private final AthenaAsyncClient athenaAsyncClient;

  public Single<String> submitQuery(String query, List<String> parameters) {
    return Single.create(emitter -> {
      try {
        QueryExecutionContext queryExecutionContext = QueryExecutionContext.builder()
            .database(athenaConfig.getDatabase())
            .build();

        StartQueryExecutionRequest.Builder requestBuilder = StartQueryExecutionRequest.builder()
            .queryString(query)
            .queryExecutionContext(queryExecutionContext)
            .resultConfiguration(ResultConfiguration.builder()
                .outputLocation(athenaConfig.getOutputLocation())
                .build());

        if (parameters != null && !parameters.isEmpty()) {
          requestBuilder.executionParameters(parameters);
          log.debug("Setting executionParameters with {} parameters", parameters.size());
        } else {
          log.debug("Skipping executionParameters (null or empty)");
        }

        StartQueryExecutionRequest request = requestBuilder.build();

        CompletableFuture<StartQueryExecutionResponse> future = athenaAsyncClient.startQueryExecution(request);
        future.whenComplete((response, throwable) -> {
          if (throwable != null) {
            log.error("Error submitting Athena query", throwable);
            emitter.onError(new RuntimeException("Failed to submit Athena query: " + throwable.getMessage(), throwable));
          } else if (response == null) {
            log.error("Athena query response is null - query: {}", query);
            emitter.onError(new RuntimeException("Athena query returned null response"));
          } else {
            String queryExecutionId = response.queryExecutionId();
            if (queryExecutionId == null || queryExecutionId.isEmpty()) {
              log.error("Query execution ID is null or empty in response - query: {}", query);
              emitter.onError(new RuntimeException("Athena query execution ID is null or empty"));
            } else {
              log.debug("Successfully submitted Athena query, execution ID: {}", queryExecutionId);
              emitter.onSuccess(queryExecutionId);
            }
          }
        });
      } catch (Exception e) {
        log.error("Error creating Athena query request", e);
        emitter.onError(new RuntimeException("Failed to create Athena query request: " + e.getMessage(), e));
      }
    });
  }

  public Single<QueryExecutionState> getQueryStatus(String queryExecutionId) {
    return Single.create(emitter -> {
      try {
        GetQueryExecutionRequest request = GetQueryExecutionRequest.builder()
            .queryExecutionId(queryExecutionId)
            .build();

        CompletableFuture<GetQueryExecutionResponse> future = athenaAsyncClient.getQueryExecution(request);
        future.whenComplete((response, throwable) -> {
          if (throwable != null) {
            log.error("Error getting query status for execution ID: {}", queryExecutionId, throwable);
            emitter.onError(new RuntimeException("Failed to get query status: " + throwable.getMessage(), throwable));
          } else if (response == null) {
            log.error("Query execution response is null for execution ID: {}", queryExecutionId);
            emitter.onError(new RuntimeException("Query execution response is null"));
          } else if (response.queryExecution() == null) {
            log.error("Query execution is null in response for execution ID: {}", queryExecutionId);
            emitter.onError(new RuntimeException("Query execution is null in response"));
          } else {
            QueryExecutionState state = response.queryExecution().status().state();
            emitter.onSuccess(state);
          }
        });
      } catch (Exception e) {
        log.error("Error creating query status request", e);
        emitter.onError(new RuntimeException("Failed to create query status request: " + e.getMessage(), e));
      }
    });
  }

  public Single<QueryExecutionState> waitForQueryCompletion(String queryExecutionId) {
    return getQueryStatus(queryExecutionId)
        .repeatWhen(completed -> completed.delay(2000L, TimeUnit.MILLISECONDS))
        .takeUntil(this::isFinalState)
        .filter(this::isFinalState)
        .firstOrError();
  }

  private boolean isFinalState(QueryExecutionState state) {
    return state == QueryExecutionState.SUCCEEDED
        || state == QueryExecutionState.FAILED
        || state == QueryExecutionState.CANCELLED;
  }

  public Single<ResultSet> getQueryResults(String queryExecutionId) {
    return getQueryResults(queryExecutionId, null, null)
        .map(ResultSetWithToken::getResultSet);
  }

  public Single<ResultSetWithToken> getQueryResults(
      String queryExecutionId, Integer maxResults, String nextToken) {
    return Single.create(emitter -> {
      try {
        GetQueryResultsRequest.Builder requestBuilder = GetQueryResultsRequest.builder()
            .queryExecutionId(queryExecutionId);

        if (maxResults != null) {
          if (maxResults < 1 || maxResults > 1000) {
            emitter.onError(new IllegalArgumentException("maxResults must be between 1 and 1000"));
            return;
          }
          requestBuilder.maxResults(maxResults);
        }

        if (nextToken != null && !nextToken.isEmpty()) {
          requestBuilder.nextToken(nextToken);
        }

        GetQueryResultsRequest request = requestBuilder.build();

        CompletableFuture<GetQueryResultsResponse> future = athenaAsyncClient.getQueryResults(request);
        future.whenComplete((response, throwable) -> {
          if (throwable != null) {
            log.error("Error fetching query results for execution ID: {}", queryExecutionId, throwable);
            emitter.onError(new RuntimeException("Failed to fetch query results: " + throwable.getMessage(), throwable));
          } else if (response == null) {
            log.error("Query results response is null for execution ID: {}", queryExecutionId);
            emitter.onError(new RuntimeException("Query results response is null"));
          } else {
            ResultSet resultSet = response.resultSet();
            String responseNextToken = response.nextToken();

            if (resultSet == null) {
              log.error("Result set is null in response for execution ID: {}", queryExecutionId);
              emitter.onError(new RuntimeException("Result set is null in response"));
            } else {
              ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, responseNextToken);
              emitter.onSuccess(resultSetWithToken);
            }
          }
        });
      } catch (Exception e) {
        log.error("Error creating query results request", e);
        emitter.onError(new RuntimeException("Failed to create query results request: " + e.getMessage(), e));
      }
    });
  }

  public Single<QueryExecution> getQueryExecution(String queryExecutionId) {
    return Single.create(emitter -> {
      try {
        GetQueryExecutionRequest request = GetQueryExecutionRequest.builder()
            .queryExecutionId(queryExecutionId)
            .build();

        CompletableFuture<GetQueryExecutionResponse> future = athenaAsyncClient.getQueryExecution(request);
        future.whenComplete((response, throwable) -> {
          if (throwable != null) {
            log.error("Error getting query execution for execution ID: {}", queryExecutionId, throwable);
            emitter.onError(new RuntimeException("Failed to get query execution: " + throwable.getMessage(), throwable));
          } else if (response == null) {
            log.error("Query execution response is null for execution ID: {}", queryExecutionId);
            emitter.onError(new RuntimeException("Query execution response is null"));
          } else {
            QueryExecution queryExecution = response.queryExecution();
            if (queryExecution == null) {
              log.error("Query execution is null in response for execution ID: {}", queryExecutionId);
              emitter.onError(new RuntimeException("Query execution is null in response"));
            } else {
              emitter.onSuccess(queryExecution);
            }
          }
        });
      } catch (Exception e) {
        log.error("Error creating query execution request", e);
        emitter.onError(new RuntimeException("Failed to create query execution request: " + e.getMessage(), e));
      }
    });
  }
}

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
import org.dreamhorizon.pulseserver.constant.Constants;
import software.amazon.awssdk.services.athena.AthenaAsyncClient;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
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
        StartQueryExecutionRequest request = buildSubmitRequest(query, parameters);
        CompletableFuture<StartQueryExecutionResponse> future = athenaAsyncClient.startQueryExecution(request);
        
        future.whenComplete((response, throwable) -> {
          if (throwable != null) {
            log.error("Error submitting Athena query", throwable);
            emitter.onError(new RuntimeException("Failed to submit Athena query: " + throwable.getMessage(), throwable));
          } else if (response == null || response.queryExecutionId() == null || response.queryExecutionId().isEmpty()) {
            log.error("Athena query response is null or execution ID is empty - query: {}", query);
            emitter.onError(new RuntimeException("Athena query returned invalid response"));
          } else {
            log.debug("Successfully submitted Athena query, execution ID: {}", response.queryExecutionId());
            emitter.onSuccess(response.queryExecutionId());
          }
        });
      } catch (Exception e) {
        log.error("Error creating Athena query request", e);
        emitter.onError(new RuntimeException("Failed to create Athena query request: " + e.getMessage(), e));
      }
    });
  }

  private StartQueryExecutionRequest buildSubmitRequest(String query, List<String> parameters) {
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
    }

    return requestBuilder.build();
  }

  public Single<QueryExecutionState> getQueryStatus(String queryExecutionId) {
    return executeAthenaRequest(
        () -> GetQueryExecutionRequest.builder().queryExecutionId(queryExecutionId).build(),
        athenaAsyncClient::getQueryExecution,
        response -> {
          if (response == null || response.queryExecution() == null) {
            throw new RuntimeException("Query execution response is null");
          }
          return response.queryExecution().status().state();
        },
        "Error getting query status for execution ID: " + queryExecutionId
    );
  }

  public Single<QueryExecutionState> waitForQueryCompletion(String queryExecutionId) {
    return getQueryStatus(queryExecutionId)
        .repeatWhen(completed -> completed.delay(Constants.ATHENA_WAIT_COMPLETION_DELAY_MS, TimeUnit.MILLISECONDS))
        .takeUntil(this::isFinalState)
        .filter(this::isFinalState)
        .firstOrError();
  }

  public Single<ResultSet> getQueryResults(String queryExecutionId) {
    return getQueryResults(queryExecutionId, null, null)
        .map(ResultSetWithToken::getResultSet);
  }

  public Single<ResultSetWithToken> getQueryResults(String queryExecutionId, Integer maxResults, String nextToken) {
    return Single.create(emitter -> {
      try {
        GetQueryResultsRequest request = buildGetResultsRequest(queryExecutionId, maxResults, nextToken);
        CompletableFuture<GetQueryResultsResponse> future = athenaAsyncClient.getQueryResults(request);
        
        future.whenComplete((response, throwable) -> {
          if (throwable != null) {
            log.error("Error fetching query results for execution ID: {}", queryExecutionId, throwable);
            emitter.onError(new RuntimeException("Failed to fetch query results: " + throwable.getMessage(), throwable));
          } else if (response == null || response.resultSet() == null) {
            log.error("Query results response is null or result set is null for execution ID: {}", queryExecutionId);
            emitter.onError(new RuntimeException("Query results response is invalid"));
          } else {
            ResultSetWithToken resultSetWithToken = new ResultSetWithToken(
                response.resultSet(), 
                response.nextToken()
            );
            emitter.onSuccess(resultSetWithToken);
          }
        });
      } catch (IllegalArgumentException e) {
        log.error("Error creating query results request", e);
        emitter.onError(e);
      } catch (Exception e) {
        log.error("Error creating query results request", e);
        emitter.onError(new RuntimeException("Failed to create query results request: " + e.getMessage(), e));
      }
    });
  }

  private GetQueryResultsRequest buildGetResultsRequest(String queryExecutionId, Integer maxResults, String nextToken) {
    GetQueryResultsRequest.Builder requestBuilder = GetQueryResultsRequest.builder()
        .queryExecutionId(queryExecutionId);

    if (maxResults != null) {
      if (maxResults < Constants.MIN_QUERY_RESULTS || maxResults > Constants.MAX_QUERY_RESULTS) {
        throw new IllegalArgumentException(
            String.format("maxResults must be between %d and %d", Constants.MIN_QUERY_RESULTS, Constants.MAX_QUERY_RESULTS));
      }
      requestBuilder.maxResults(maxResults);
    }

    if (nextToken != null && !nextToken.isEmpty()) {
      requestBuilder.nextToken(nextToken);
    }

    return requestBuilder.build();
  }

  public Single<QueryExecution> getQueryExecution(String queryExecutionId) {
    return executeAthenaRequest(
        () -> GetQueryExecutionRequest.builder().queryExecutionId(queryExecutionId).build(),
        athenaAsyncClient::getQueryExecution,
        response -> {
          if (response == null || response.queryExecution() == null) {
            throw new RuntimeException("Query execution is null in response");
          }
          return response.queryExecution();
        },
        "Error getting query execution for execution ID: " + queryExecutionId
    );
  }

  private <TRequest, TResponse, TResult> Single<TResult> executeAthenaRequest(
      java.util.function.Supplier<TRequest> requestBuilder,
      java.util.function.Function<TRequest, CompletableFuture<TResponse>> asyncCall,
      java.util.function.Function<TResponse, TResult> responseMapper,
      String errorContext) {
    return Single.create(emitter -> {
      try {
        TRequest request = requestBuilder.get();
        CompletableFuture<TResponse> future = asyncCall.apply(request);
        
        future.whenComplete((response, throwable) -> {
          if (throwable != null) {
            log.error(errorContext, throwable);
            emitter.onError(new RuntimeException(errorContext + ": " + throwable.getMessage(), throwable));
          } else {
            try {
              TResult result = responseMapper.apply(response);
              emitter.onSuccess(result);
            } catch (Exception e) {
              log.error(errorContext, e);
              emitter.onError(new RuntimeException(errorContext + ": " + e.getMessage(), e));
            }
          }
        });
      } catch (IllegalArgumentException e) {
        log.error("Error creating request: {}", errorContext, e);
        emitter.onError(e);
      } catch (Exception e) {
        log.error("Error creating request: {}", errorContext, e);
        emitter.onError(new RuntimeException("Failed to create request: " + e.getMessage(), e));
      }
    });
  }

  private boolean isFinalState(QueryExecutionState state) {
    return state == QueryExecutionState.SUCCEEDED
        || state == QueryExecutionState.FAILED
        || state == QueryExecutionState.CANCELLED;
  }
}

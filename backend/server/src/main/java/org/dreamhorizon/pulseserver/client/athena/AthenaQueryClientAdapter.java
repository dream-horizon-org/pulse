package org.dreamhorizon.pulseserver.client.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.query.QueryClient;
import org.dreamhorizon.pulseserver.client.query.models.QueryExecutionInfo;
import org.dreamhorizon.pulseserver.client.query.models.QueryResultSet;
import org.dreamhorizon.pulseserver.client.query.models.QueryStatus;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AthenaQueryClientAdapter implements QueryClient {
  private final AthenaClient athenaClient;
  private final AthenaResultConverter resultConverter;

  @Override
  public Single<String> submitQuery(String query, List<String> parameters) {
    return athenaClient.submitQuery(query, parameters);
  }

  @Override
  public Single<QueryStatus> getQueryStatus(String queryExecutionId) {
    return athenaClient.getQueryStatus(queryExecutionId)
        .map(this::mapToQueryStatus);
  }

  @Override
  public Single<QueryStatus> waitForQueryCompletion(String queryExecutionId) {
    return athenaClient.waitForQueryCompletion(queryExecutionId)
        .map(this::mapToQueryStatus);
  }

  @Override
  public Single<QueryResultSet> getQueryResults(String queryExecutionId, Integer maxResults, String nextToken) {
    return athenaClient.getQueryResults(queryExecutionId, maxResults, nextToken)
        .map(resultSetWithToken -> QueryResultSet.builder()
            .resultData(resultConverter.convertToJsonArray(resultSetWithToken.getResultSet()))
            .nextToken(resultSetWithToken.getNextToken())
            .build());
  }

  @Override
  public Single<QueryExecutionInfo> getQueryExecution(String queryExecutionId) {
    return athenaClient.getQueryExecution(queryExecutionId)
        .map(this::mapToQueryExecutionInfo);
  }

  private QueryStatus mapToQueryStatus(QueryExecutionState state) {
    switch (state) {
      case QUEUED:
        return QueryStatus.QUEUED;
      case RUNNING:
        return QueryStatus.RUNNING;
      case SUCCEEDED:
        return QueryStatus.SUCCEEDED;
      case FAILED:
        return QueryStatus.FAILED;
      case CANCELLED:
        return QueryStatus.CANCELLED;
      default:
        return QueryStatus.RUNNING;
    }
  }

  private QueryExecutionInfo mapToQueryExecutionInfo(QueryExecution execution) {
    String resultLocation = execution.resultConfiguration() != null
        ? execution.resultConfiguration().outputLocation()
        : null;

    Long dataScannedBytes = execution.statistics() != null
        ? execution.statistics().dataScannedInBytes()
        : null;

    return QueryExecutionInfo.builder()
        .queryExecutionId(execution.queryExecutionId())
        .status(mapToQueryStatus(execution.status().state()))
        .stateChangeReason(execution.status().stateChangeReason())
        .resultLocation(resultLocation)
        .dataScannedInBytes(dataScannedBytes)
        .build();
  }
}

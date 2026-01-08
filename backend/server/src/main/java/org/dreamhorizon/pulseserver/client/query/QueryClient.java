package org.dreamhorizon.pulseserver.client.query;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.client.query.models.QueryExecutionInfo;
import org.dreamhorizon.pulseserver.client.query.models.QueryResultSet;
import org.dreamhorizon.pulseserver.client.query.models.QueryStatus;

public interface QueryClient {
  Single<String> submitQuery(String query, List<String> parameters);

  Single<QueryStatus> getQueryStatus(String queryExecutionId);

  Single<QueryStatus> waitForQueryCompletion(String queryExecutionId);

  Single<QueryResultSet> getQueryResults(String queryExecutionId, Integer maxResults, String nextToken);

  Single<QueryExecutionInfo> getQueryExecution(String queryExecutionId);
}


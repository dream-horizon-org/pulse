package org.dreamhorizon.pulseserver.service.query;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.TableMetadata;

public interface QueryService {
  Single<QueryJob> submitQuery(String queryString, String userEmail);

  Single<QueryJob> getJobStatus(String jobId, Integer maxResults, String nextToken);

  Single<QueryJob> waitForJobCompletion(String jobId);

  Single<QueryJob> cancelQuery(String jobId);

  Single<List<QueryJob>> getQueryHistory(String userEmail, Integer limit, Integer offset);

  Single<List<TableMetadata>> getTablesAndColumns();
}


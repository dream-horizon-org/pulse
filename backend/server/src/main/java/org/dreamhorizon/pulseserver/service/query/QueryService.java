package org.dreamhorizon.pulseserver.service.query;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;

public interface QueryService {
  Single<QueryJob> submitQuery(String queryString, List<String> parameters, String timestampString);

  Single<QueryJob> getJobStatus(String jobId, Integer maxResults, String nextToken);

  Single<QueryJob> waitForJobCompletion(String jobId);
}


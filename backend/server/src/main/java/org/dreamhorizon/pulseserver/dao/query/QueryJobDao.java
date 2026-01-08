package org.dreamhorizon.pulseserver.dao.query;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;

public interface QueryJobDao {
  Single<String> createJob(String queryString);

  Single<Boolean> updateJobWithExecutionId(String jobId, String queryExecutionId, QueryJobStatus status);

  Single<Boolean> updateJobStatus(String jobId, QueryJobStatus status);

  Single<Boolean> updateJobCompleted(String jobId, String resultLocation);

  Single<Boolean> updateJobFailed(String jobId, String errorMessage);

  Single<QueryJob> getJobById(String jobId);
}


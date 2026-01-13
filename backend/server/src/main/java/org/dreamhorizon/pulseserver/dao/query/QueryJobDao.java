package org.dreamhorizon.pulseserver.dao.query;

import io.reactivex.rxjava3.core.Single;
import java.sql.Timestamp;
import java.util.List;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;

public interface QueryJobDao {
  Single<String> createJob(String queryString, String originalQueryString, String userEmail);

  Single<Boolean> updateJobWithExecutionId(String jobId, String queryExecutionId, QueryJobStatus status, Timestamp submissionDateTime);

  Single<Boolean> updateJobStatus(String jobId, QueryJobStatus status, Timestamp updatedAt);

  Single<Boolean> updateJobCompleted(String jobId, String resultLocation, Timestamp completionDateTime);

  Single<Boolean> updateJobFailed(String jobId, String errorMessage, Timestamp completionDateTime);

  Single<QueryJob> getJobById(String jobId);

  Single<List<QueryJob>> getQueryHistory(String userEmail, Integer limit, Integer offset);

  Single<Boolean> updateJobStatistics(String jobId, Long dataScannedInBytes, 
      Long executionTimeMillis, Long engineExecutionTimeMillis, Long queryQueueTimeMillis, Timestamp updatedAt);

  Single<List<QueryJob>> getQueriesForStatistics(String userEmail, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate);
}


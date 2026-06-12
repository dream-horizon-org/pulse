package org.dreamhorizon.pulseserver.dao.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.sql.Timestamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AthenaQueryJobDaoAdapter implements QueryJobDao {
  private final AthenaJobDao athenaJobDao;

  @Override
  public Single<String> createJob(String projectId, String queryString, String userEmail) {
    return athenaJobDao.createJob(projectId, queryString, userEmail);
  }

  @Override
  public Single<Boolean> updateJobWithExecutionId(String jobId, String queryExecutionId, QueryJobStatus status,
                                                  Timestamp submissionDateTime) {
    return athenaJobDao.updateJobWithExecutionId(jobId, queryExecutionId, mapToAthenaStatus(status), submissionDateTime);
  }

  @Override
  public Single<Boolean> updateJobStatus(String jobId, QueryJobStatus status, Timestamp updatedAt) {
    return athenaJobDao.updateJobStatus(jobId, mapToAthenaStatus(status), updatedAt);
  }

  @Override
  public Single<Boolean> updateJobCompleted(String jobId, String resultLocation, Timestamp completionDateTime) {
    return athenaJobDao.updateJobCompleted(jobId, resultLocation, completionDateTime);
  }

  @Override
  public Single<Boolean> updateJobFailed(String jobId, String errorMessage, Timestamp completionDateTime) {
    return athenaJobDao.updateJobFailed(jobId, errorMessage, completionDateTime);
  }

  @Override
  public Single<QueryJob> getJobById(String jobId) {
    return athenaJobDao.getJobById(jobId)
        .map(this::mapToQueryJob);
  }

  private AthenaJobStatus mapToAthenaStatus(QueryJobStatus status) {
    return AthenaJobStatus.valueOf(status.name());
  }

  @Override
  public Single<List<QueryJob>> getQueryHistory(String userEmail, Integer limit, Integer offset) {
    return athenaJobDao.getQueryHistory(userEmail, limit, offset)
        .map(jobs -> jobs.stream()
            .map(this::mapToQueryJob)
            .toList());
  }

  @Override
  public Single<Boolean> updateJobStatistics(String jobId, Long dataScannedInBytes,
                                             Long executionTimeMillis, Long engineExecutionTimeMillis, Long queryQueueTimeMillis,
                                             Timestamp updatedAt) {
    return athenaJobDao.updateJobStatistics(jobId, dataScannedInBytes, executionTimeMillis,
        engineExecutionTimeMillis, queryQueueTimeMillis, updatedAt);
  }

  @Override
  public Single<List<QueryJob>> getQueriesForStatistics(String userEmail, java.time.LocalDateTime startDate,
                                                        java.time.LocalDateTime endDate) {
    return athenaJobDao.getQueriesForStatistics(userEmail, startDate, endDate)
        .map(jobs -> jobs.stream()
            .map(this::mapToQueryJob)
            .toList());
  }

  private QueryJob mapToQueryJob(AthenaJob athenaJob) {
    if (athenaJob == null) {
      return null;
    }

    return QueryJob.builder()
        .jobId(athenaJob.getJobId())
        .queryString(athenaJob.getQueryString())
        .userEmail(athenaJob.getUserEmail())
        .queryExecutionId(athenaJob.getQueryExecutionId())
        .status(QueryJobStatus.valueOf(athenaJob.getStatus().name()))
        .resultLocation(athenaJob.getResultLocation())
        .errorMessage(athenaJob.getErrorMessage())
        .resultData(athenaJob.getResultData())
        .nextToken(athenaJob.getNextToken())
        .dataScannedInBytes(athenaJob.getDataScannedInBytes())
        .executionTimeMillis(athenaJob.getExecutionTimeMillis())
        .engineExecutionTimeMillis(athenaJob.getEngineExecutionTimeMillis())
        .queryQueueTimeMillis(athenaJob.getQueryQueueTimeMillis())
        .createdAt(athenaJob.getCreatedAt())
        .updatedAt(athenaJob.getUpdatedAt())
        .completedAt(athenaJob.getCompletedAt())
        .build();
  }
}

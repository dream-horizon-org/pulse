package org.dreamhorizon.pulseserver.dao.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
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
  public Single<String> createJob(String queryString) {
    return athenaJobDao.createJob(queryString);
  }

  @Override
  public Single<Boolean> updateJobWithExecutionId(String jobId, String queryExecutionId, QueryJobStatus status) {
    return athenaJobDao.updateJobWithExecutionId(jobId, queryExecutionId, mapToAthenaStatus(status));
  }

  @Override
  public Single<Boolean> updateJobStatus(String jobId, QueryJobStatus status) {
    return athenaJobDao.updateJobStatus(jobId, mapToAthenaStatus(status));
  }

  @Override
  public Single<Boolean> updateJobCompleted(String jobId, String resultLocation) {
    return athenaJobDao.updateJobCompleted(jobId, resultLocation);
  }

  @Override
  public Single<Boolean> updateJobFailed(String jobId, String errorMessage) {
    return athenaJobDao.updateJobFailed(jobId, errorMessage);
  }

  @Override
  public Single<QueryJob> getJobById(String jobId) {
    return athenaJobDao.getJobById(jobId)
        .map(this::mapToQueryJob);
  }

  private AthenaJobStatus mapToAthenaStatus(QueryJobStatus status) {
    return AthenaJobStatus.valueOf(status.name());
  }

  private QueryJob mapToQueryJob(AthenaJob athenaJob) {
    if (athenaJob == null) {
      return null;
    }

    return QueryJob.builder()
        .jobId(athenaJob.getJobId())
        .queryString(athenaJob.getQueryString())
        .queryExecutionId(athenaJob.getQueryExecutionId())
        .status(QueryJobStatus.valueOf(athenaJob.getStatus().name()))
        .resultLocation(athenaJob.getResultLocation())
        .errorMessage(athenaJob.getErrorMessage())
        .resultData(athenaJob.getResultData())
        .nextToken(athenaJob.getNextToken())
        .dataScannedInBytes(athenaJob.getDataScannedInBytes())
        .createdAt(athenaJob.getCreatedAt())
        .updatedAt(athenaJob.getUpdatedAt())
        .completedAt(athenaJob.getCompletedAt())
        .build();
  }
}

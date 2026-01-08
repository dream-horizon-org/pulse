package org.dreamhorizon.pulseserver.dao.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AthenaJobDao {
  private final MysqlClient mysqlClient;

  public Single<String> createJob(String queryString) {
    String jobId = UUID.randomUUID().toString();
    return executeUpdate(
        AthenaJobQueries.CREATE_JOB,
        Tuple.of(jobId, queryString),
        jobId,
        "Error creating Athena job"
    );
  }

  public Single<Boolean> updateJobWithExecutionId(String jobId, String queryExecutionId, AthenaJobStatus status) {
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_WITH_EXECUTION_ID,
        Tuple.of(queryExecutionId, status.name(), jobId),
        true,
        "Error updating job with execution ID: " + jobId
    );
  }

  public Single<Boolean> updateJobStatus(String jobId, AthenaJobStatus status) {
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_STATUS,
        Tuple.of(status.name(), jobId),
        true,
        "Error updating job status: " + jobId
    );
  }

  public Single<Boolean> updateJobCompleted(String jobId, String resultLocation) {
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_COMPLETED,
        Tuple.of(resultLocation, jobId),
        true,
        "Error updating job as completed: " + jobId
    );
  }

  public Single<Boolean> updateJobFailed(String jobId, String errorMessage) {
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_FAILED,
        Tuple.of(errorMessage, jobId),
        true,
        "Error updating job as failed: " + jobId
    );
  }

  public Single<AthenaJob> getJobById(String jobId) {
    return mysqlClient.getReaderPool()
        .preparedQuery(AthenaJobQueries.GET_JOB_BY_ID)
        .rxExecute(Tuple.of(jobId))
        .map(rowSet -> {
          if (rowSet.size() == 0) {
            log.warn("Job not found: {}", jobId);
            return null;
          }
          
          return mapRowToAthenaJob(rowSet.iterator().next());
        })
        .onErrorResumeNext(error -> {
          log.error("Error fetching job: {}", jobId, error);
          return Single.error(new RuntimeException("Failed to fetch job: " + error.getMessage(), error));
        });
  }

  private <T> Single<T> executeUpdate(String query, Tuple parameters, T result, String errorMessage) {
    return mysqlClient.getWriterPool()
        .preparedQuery(query)
        .rxExecute(parameters)
        .map(r -> result)
        .onErrorResumeNext(error -> {
          log.error(errorMessage, error);
          return Single.error(new RuntimeException(errorMessage + ": " + error.getMessage(), error));
        });
  }

  private AthenaJob mapRowToAthenaJob(io.vertx.rxjava3.sqlclient.Row row) {
    return AthenaJob.builder()
        .jobId(row.getString("job_id"))
        .queryString(row.getString("query_string"))
        .queryExecutionId(row.getString("query_execution_id"))
        .status(AthenaJobStatus.valueOf(row.getString("status")))
        .resultLocation(row.getString("result_location"))
        .errorMessage(row.getString("error_message"))
        .resultData(null)
        .createdAt(convertToTimestamp(row.getLocalDateTime("created_at")))
        .updatedAt(convertToTimestamp(row.getLocalDateTime("updated_at")))
        .completedAt(convertToTimestamp(row.getLocalDateTime("completed_at")))
        .build();
  }

  private Timestamp convertToTimestamp(java.time.LocalDateTime localDateTime) {
    return localDateTime != null ? Timestamp.valueOf(localDateTime) : null;
  }
}

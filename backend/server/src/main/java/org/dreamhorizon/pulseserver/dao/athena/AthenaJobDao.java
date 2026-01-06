package org.dreamhorizon.pulseserver.dao.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.sql.Timestamp;
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
    String jobId = java.util.UUID.randomUUID().toString();
    return mysqlClient.getWriterPool()
        .preparedQuery(AthenaJobQueries.CREATE_JOB)
        .rxExecute(Tuple.of(jobId, queryString))
        .map(rowSet -> jobId)
        .onErrorResumeNext(error -> {
          log.error("Error creating Athena job", error);
          return Single.error(new RuntimeException("Failed to create Athena job: " + error.getMessage(), error));
        });
  }

  public Single<Boolean> updateJobWithExecutionId(String jobId, String queryExecutionId, AthenaJobStatus status) {
    return mysqlClient.getWriterPool()
        .preparedQuery(AthenaJobQueries.UPDATE_JOB_WITH_EXECUTION_ID)
        .rxExecute(Tuple.of(queryExecutionId, status.name(), jobId))
        .map(r -> true)
        .onErrorResumeNext(error -> {
          log.error("Error updating job with execution ID: {}", jobId, error);
          return Single.error(new RuntimeException("Failed to update job: " + error.getMessage(), error));
        });
  }

  public Single<Boolean> updateJobStatus(String jobId, AthenaJobStatus status) {
    return mysqlClient.getWriterPool()
        .preparedQuery(AthenaJobQueries.UPDATE_JOB_STATUS)
        .rxExecute(Tuple.of(status.name(), jobId))
        .map(r -> true)
        .onErrorResumeNext(error -> {
          log.error("Error updating job status: {}", jobId, error);
          return Single.error(new RuntimeException("Failed to update job status: " + error.getMessage(), error));
        });
  }

  public Single<Boolean> updateJobCompleted(String jobId, String resultLocation) {
    return mysqlClient.getWriterPool()
        .preparedQuery(AthenaJobQueries.UPDATE_JOB_COMPLETED)
        .rxExecute(Tuple.of(resultLocation, jobId))
        .map(r -> true)
        .onErrorResumeNext(error -> {
          log.error("Error updating job as completed: {}", jobId, error);
          return Single.error(new RuntimeException("Failed to update job: " + error.getMessage(), error));
        });
  }

  public Single<Boolean> updateJobFailed(String jobId, String errorMessage) {
    return mysqlClient.getWriterPool()
        .preparedQuery(AthenaJobQueries.UPDATE_JOB_FAILED)
        .rxExecute(Tuple.of(errorMessage, jobId))
        .map(r -> true)
        .onErrorResumeNext(error -> {
          log.error("Error updating job as failed: {}", jobId, error);
          return Single.error(new RuntimeException("Failed to update job: " + error.getMessage(), error));
        });
  }

  public Single<AthenaJob> getJobById(String jobId) {
    return mysqlClient.getReaderPool()
        .preparedQuery(AthenaJobQueries.GET_JOB_BY_ID)
        .rxExecute(Tuple.of(jobId))
        .map((io.vertx.rxjava3.sqlclient.RowSet<io.vertx.rxjava3.sqlclient.Row> rowSet) -> {
          if (rowSet.size() == 0) {
            log.warn("Job not found: {}", jobId);
            return null;
          }
          
          io.vertx.rxjava3.sqlclient.Row row = rowSet.iterator().next();
          
          return AthenaJob.builder()
              .jobId(row.getString("job_id"))
              .queryString(row.getString("query_string"))
              .queryExecutionId(row.getString("query_execution_id"))
              .status(AthenaJobStatus.valueOf(row.getString("status")))
              .resultLocation(row.getString("result_location"))
              .errorMessage(row.getString("error_message"))
              .resultData(null) // Results are fetched from Athena API, not stored in DB
              .createdAt(convertToTimestamp(row.getLocalDateTime("created_at")))
              .updatedAt(convertToTimestamp(row.getLocalDateTime("updated_at")))
              .completedAt(row.getLocalDateTime("completed_at") != null 
                  ? convertToTimestamp(row.getLocalDateTime("completed_at")) 
                  : null)
              .build();
        })
        .onErrorResumeNext(error -> {
          log.error("Error fetching job: {}", jobId, error);
          return Single.error(new RuntimeException("Failed to fetch job: " + error.getMessage(), error));
        });
  }

  private Timestamp convertToTimestamp(java.time.LocalDateTime localDateTime) {
    return localDateTime != null ? Timestamp.valueOf(localDateTime) : null;
  }
}


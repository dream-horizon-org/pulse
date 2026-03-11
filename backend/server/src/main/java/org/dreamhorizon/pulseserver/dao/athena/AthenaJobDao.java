package org.dreamhorizon.pulseserver.dao.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;

/**
 * DAO for Athena job operations.
 * Updated to use project-based isolation while maintaining tenant hierarchy.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AthenaJobDao {
  private final MysqlClient mysqlClient;

  /**
   * Gets the current project ID from the ProjectContext.
   */
  private String getProjectId() {
    return ProjectContext.getProjectId();
  }

  public Single<String> createJob(String projectId, String queryString, String userEmail) {
    String jobId = UUID.randomUUID().toString();
    return executeUpdate(
        AthenaJobQueries.CREATE_JOB,
        Tuple.of(jobId, projectId, queryString, userEmail),
        jobId,
        "Error creating Athena job"
    );
  }

  public Single<Boolean> updateJobWithExecutionId(String jobId, String queryExecutionId, AthenaJobStatus status,
                                                  Timestamp submissionDateTime) {
    Timestamp updatedAt = submissionDateTime != null ? submissionDateTime : new Timestamp(System.currentTimeMillis());
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_WITH_EXECUTION_ID,
        Tuple.of(queryExecutionId, status.name(), submissionDateTime, updatedAt, jobId),
        true,
        "Error updating job with execution ID: " + jobId
    );
  }

  public Single<Boolean> updateJobStatus(String jobId, AthenaJobStatus status, Timestamp updatedAt) {
    Timestamp finalUpdatedAt = updatedAt != null ? updatedAt : new Timestamp(System.currentTimeMillis());
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_STATUS,
        Tuple.of(status.name(), finalUpdatedAt, jobId),
        true,
        "Error updating job status: " + jobId
    );
  }

  public Single<Boolean> updateJobCompleted(String jobId, String resultLocation,
                                            Timestamp completionDateTime) {
    Timestamp updatedAt = completionDateTime != null ? completionDateTime : new Timestamp(System.currentTimeMillis());
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_COMPLETED,
        Tuple.of(resultLocation, completionDateTime, updatedAt, jobId),
        true,
        "Error updating job as completed: " + jobId
    );
  }

  public Single<Boolean> updateJobStatistics(String jobId, Long dataScannedInBytes,
                                             Long executionTimeMillis, Long engineExecutionTimeMillis, Long queryQueueTimeMillis,
                                             Timestamp updatedAt) {
    Timestamp finalUpdatedAt = updatedAt != null ? updatedAt : new Timestamp(System.currentTimeMillis());
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_STATISTICS,
        Tuple.wrap(Arrays.asList(dataScannedInBytes, executionTimeMillis, engineExecutionTimeMillis,
            queryQueueTimeMillis, finalUpdatedAt, jobId)),
        true,
        "Error updating job statistics: " + jobId
    );
  }

  public Single<Boolean> updateJobFailed(String jobId, String errorMessage, Timestamp completionDateTime) {
    Timestamp updatedAt = completionDateTime != null ? completionDateTime : new Timestamp(System.currentTimeMillis());
    return executeUpdate(
        AthenaJobQueries.UPDATE_JOB_FAILED,
        Tuple.of(errorMessage, completionDateTime, updatedAt, jobId),
        true,
        "Error updating job as failed: " + jobId
    );
  }

  public Single<AthenaJob> getJobById(String jobId) {
    return mysqlClient.getWriterPool()
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

  public Single<List<AthenaJob>> getQueryHistory(String userEmail, Integer limit, Integer offset) {
    return mysqlClient.getReaderPool()
        .preparedQuery(AthenaJobQueries.GET_QUERY_HISTORY)
        .rxExecute(Tuple.of(getProjectId(), userEmail, limit, offset))
        .map(rowSet -> {
          List<AthenaJob> jobs = new ArrayList<>();
          for (io.vertx.rxjava3.sqlclient.Row row : rowSet) {
            jobs.add(mapRowToAthenaJob(row));
          }
          return jobs;
        })
        .onErrorResumeNext(error -> {
          log.error("Error fetching query history for user: {}", userEmail, error);
          return Single.error(new RuntimeException("Failed to fetch query history: " + error.getMessage(), error));
        });
  }

  public Single<List<AthenaJob>> getQueriesForStatistics(String userEmail, java.time.LocalDateTime startDate,
                                                         java.time.LocalDateTime endDate) {
    return mysqlClient.getReaderPool()
        .preparedQuery(AthenaJobQueries.GET_QUERIES_FOR_STATISTICS)
        .rxExecute(Tuple.of(getProjectId(), userEmail, startDate, endDate))
        .map(rowSet -> {
          List<AthenaJob> jobs = new ArrayList<>();
          for (io.vertx.rxjava3.sqlclient.Row row : rowSet) {
            jobs.add(mapRowToAthenaJob(row));
          }
          return jobs;
        })
        .onErrorResumeNext(error -> {
          log.error("Error fetching queries for statistics for user: {}", userEmail, error);
          return Single.error(new RuntimeException("Failed to fetch queries for statistics: " + error.getMessage(), error));
        });
  }

  private AthenaJob mapRowToAthenaJob(io.vertx.rxjava3.sqlclient.Row row) {
    return AthenaJob.builder()
        .jobId(row.getString("job_id"))
        .tenantId(row.getString("tenant_id"))
        .projectId(row.getString("project_id"))
        .queryString(row.getString("query_string"))
        .userEmail(row.getString("user_email"))
        .queryExecutionId(row.getString("query_execution_id"))
        .status(AthenaJobStatus.valueOf(row.getString("status")))
        .resultLocation(row.getString("result_location"))
        .errorMessage(row.getString("error_message"))
        .dataScannedInBytes(getLongValue(row, "data_scanned_in_bytes"))
        .executionTimeMillis(getLongValue(row, "execution_time_millis"))
        .engineExecutionTimeMillis(getLongValue(row, "engine_execution_time_millis"))
        .queryQueueTimeMillis(getLongValue(row, "query_queue_time_millis"))
        .resultData(null)
        .createdAt(convertToTimestamp(row.getLocalDateTime("created_at")))
        .updatedAt(convertToTimestamp(row.getLocalDateTime("updated_at")))
        .completedAt(convertToTimestamp(row.getLocalDateTime("completed_at")))
        .build();
  }

  private Long getLongValue(io.vertx.rxjava3.sqlclient.Row row, String columnName) {
    try {
      Long value = row.getLong(columnName);
      return value;
    } catch (Exception e) {
      return null;
    }
  }

  private Timestamp convertToTimestamp(java.time.LocalDateTime localDateTime) {
    return localDateTime != null ? Timestamp.valueOf(localDateTime) : null;
  }
}

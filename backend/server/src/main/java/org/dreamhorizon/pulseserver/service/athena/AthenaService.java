package org.dreamhorizon.pulseserver.service.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.athena.AthenaClient;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.athena.AthenaJobDao;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.Row;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AthenaService {

  private final AthenaClient athenaClient;
  private final AthenaJobDao athenaJobDao;

  public Single<AthenaJob> getJobStatus(String jobId, Integer maxResults, String nextToken) {
    return athenaJobDao.getJobById(jobId)
        .flatMap(job -> {
          if (job == null) {
            return Single.error(new RuntimeException("Job not found: " + jobId));
          }

          if (isFinalState(job.getStatus())) {
            if (job.getStatus() == AthenaJobStatus.COMPLETED && job.getQueryExecutionId() != null) {
              return fetchPaginatedResults(job, maxResults, nextToken);
            }
            return Single.just(job);
          }

          if (job.getQueryExecutionId() == null) {
            return Single.just(job);
          }

          return athenaClient.getQueryStatus(job.getQueryExecutionId())
              .flatMap(state -> {
                AthenaJobStatus newStatus = mapAthenaStateToJobStatus(state);

                if (newStatus != job.getStatus()) {
                  return handleStatusChange(jobId, job.getQueryExecutionId(), newStatus, job, maxResults, nextToken);
                }

                if (newStatus == AthenaJobStatus.COMPLETED && (maxResults != null || nextToken != null)) {
                  return fetchPaginatedResults(job, maxResults, nextToken);
                }

                return Single.just(job);
              });
        });
  }

  private Single<AthenaJob> handleStatusChange(String jobId, String queryExecutionId, AthenaJobStatus newStatus,
                                               AthenaJob job, Integer maxResults, String nextToken) {
    if (newStatus == AthenaJobStatus.COMPLETED) {
      return fetchAndUpdateJobResults(jobId, queryExecutionId)
          .flatMap(updatedJob -> {
            if (maxResults != null || nextToken != null) {
              return fetchPaginatedResults(updatedJob, maxResults, nextToken);
            }
            return Single.just(updatedJob);
          });
    } else if (newStatus == AthenaJobStatus.FAILED || newStatus == AthenaJobStatus.CANCELLED) {
      return handleFailedQueryInStatusCheck(jobId, queryExecutionId, newStatus);
    } else {
      return athenaClient.getQueryExecution(queryExecutionId)
          .flatMap(execution -> {
            Timestamp updatedAt = extractCompletionDateTime(execution);
            if (updatedAt == null) {
              updatedAt = extractSubmissionDateTime(execution);
            }
            if (updatedAt == null) {
              updatedAt = new Timestamp(System.currentTimeMillis());
            }
            return athenaJobDao.updateJobStatus(jobId, newStatus, updatedAt)
                .flatMap(v -> athenaJobDao.getJobById(jobId));
          });
    }
  }

  private Single<AthenaJob> handleFailedQueryInStatusCheck(String jobId, String queryExecutionId, AthenaJobStatus status) {
    return athenaClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String errorMessage = execution.status().stateChangeReason() != null
              ? execution.status().stateChangeReason()
              : "Query " + status.name().toLowerCase();
          Long dataScannedBytes = extractDataScannedBytes(execution);
          Timestamp completionDateTime = extractCompletionDateTime(execution);
          return athenaJobDao.updateJobFailed(jobId, errorMessage, completionDateTime)
              .flatMap(v -> athenaJobDao.getJobById(jobId)
                  .map(dbJob -> buildJobWithDataScanned(dbJob, dataScannedBytes)));
        });
  }

  private Single<AthenaJob> fetchPaginatedResults(AthenaJob job, Integer maxResults, String nextToken) {
    if (job.getQueryExecutionId() == null) {
      return Single.just(job);
    }

    Integer athenaMaxResults = maxResults != null ? Math.min(maxResults + 1, Constants.MAX_QUERY_RESULTS) : null;

    return athenaClient.getQueryResults(job.getQueryExecutionId(), athenaMaxResults, nextToken)
        .map(resultSetWithToken -> {
          JsonArray resultData = convertResultSetToJsonArray(resultSetWithToken.getResultSet());
          return buildJobWithResults(job, resultData, resultSetWithToken.getNextToken());
        });
  }

  public Single<AthenaJob> waitForJobCompletion(String jobId) {
    return athenaJobDao.getJobById(jobId)
        .flatMap(job -> {
          if (job == null) {
            return Single.error(new RuntimeException("Job not found: " + jobId));
          }

          if (isFinalState(job.getStatus())) {
            return Single.just(job);
          }

          if (job.getQueryExecutionId() == null) {
            return Single.error(new RuntimeException("No query execution ID for job: " + jobId));
          }

          return athenaClient.waitForQueryCompletion(job.getQueryExecutionId())
              .flatMap(state -> {
                if (state == QueryExecutionState.SUCCEEDED) {
                  return fetchAndUpdateJobResults(jobId, job.getQueryExecutionId());
                } else {
                  return handleFailedQueryInStatusCheck(jobId, job.getQueryExecutionId(),
                      mapAthenaStateToJobStatus(state));
                }
              });
        });
  }

  private Single<AthenaJob> fetchAndUpdateJobResults(String jobId, String queryExecutionId) {
    return athenaClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String resultLocation = execution.resultConfiguration() != null
              ? execution.resultConfiguration().outputLocation()
              : null;
          Long dataScannedBytes = extractDataScannedBytes(execution);
          Timestamp completionDateTime = extractCompletionDateTime(execution);

          return athenaJobDao.updateJobCompleted(jobId, resultLocation, completionDateTime)
              .flatMap(v -> athenaJobDao.getJobById(jobId)
                  .map(job -> buildJobWithDataScanned(job, dataScannedBytes)));
        })
        .onErrorResumeNext(error -> {
          log.error("Error updating job results location for job: {}", jobId, error);
          return athenaClient.getQueryExecution(queryExecutionId)
              .flatMap(execution -> {
                Timestamp completionDateTime = extractCompletionDateTime(execution);
                return athenaJobDao.updateJobFailed(jobId, "Failed to update result location: " + error.getMessage(), completionDateTime)
                    .flatMap(v -> athenaJobDao.getJobById(jobId));
              })
              .onErrorResumeNext(fallbackError -> {
                return athenaJobDao.updateJobFailed(jobId, "Failed to update result location: " + error.getMessage(), null)
                    .flatMap(v -> athenaJobDao.getJobById(jobId));
              });
        });
  }

  private JsonArray convertResultSetToJsonArray(ResultSet resultSet) {
    JsonArray result = new JsonArray();

    if (resultSet.resultSetMetadata() == null || resultSet.resultSetMetadata().columnInfo() == null) {
      return result;
    }

    List<String> columnNames = new ArrayList<>();
    resultSet.resultSetMetadata().columnInfo().forEach(column -> columnNames.add(column.name()));

    if (resultSet.rows() != null) {
      boolean isFirstRow = true;
      for (Row row : resultSet.rows()) {
        if (isFirstRow) {
          isFirstRow = false;
          continue;
        }

        JsonObject rowObject = new JsonObject();
        for (int i = 0; i < columnNames.size() && i < row.data().size(); i++) {
          String columnName = columnNames.get(i);
          String value = row.data().get(i).varCharValue();
          rowObject.put(columnName, value);
        }
        result.add(rowObject);
      }
    }

    return result;
  }

  private Long extractDataScannedBytes(QueryExecution execution) {
    return extractDataScannedBytes(execution, null);
  }

  private Long extractDataScannedBytes(QueryExecution execution, Long fallback) {
    return execution.statistics() != null && execution.statistics().dataScannedInBytes() != null
        ? execution.statistics().dataScannedInBytes()
        : fallback;
  }

  private Timestamp extractSubmissionDateTime(QueryExecution execution) {
    if (execution.status() != null && execution.status().submissionDateTime() != null) {
      return Timestamp.from(execution.status().submissionDateTime());
    }
    return null;
  }

  private Timestamp extractCompletionDateTime(QueryExecution execution) {
    if (execution.status() != null && execution.status().completionDateTime() != null) {
      return Timestamp.from(execution.status().completionDateTime());
    }
    return null;
  }

  private AthenaJob buildJobWithDataScanned(AthenaJob job, Long dataScannedBytes) {
    return AthenaJob.builder()
        .jobId(job.getJobId())
        .queryString(job.getQueryString())
        .queryExecutionId(job.getQueryExecutionId())
        .status(job.getStatus())
        .resultLocation(job.getResultLocation())
        .errorMessage(job.getErrorMessage())
        .dataScannedInBytes(dataScannedBytes)
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }

  private AthenaJob buildJobWithResults(AthenaJob job, JsonArray resultData, String nextToken) {
    return AthenaJob.builder()
        .jobId(job.getJobId())
        .queryString(job.getQueryString())
        .queryExecutionId(job.getQueryExecutionId())
        .status(job.getStatus())
        .resultLocation(job.getResultLocation())
        .errorMessage(job.getErrorMessage())
        .resultData(resultData)
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .completedAt(job.getCompletedAt())
        .nextToken(nextToken)
        .dataScannedInBytes(job.getDataScannedInBytes())
        .build();
  }

  private boolean isFinalState(AthenaJobStatus status) {
    return status == AthenaJobStatus.COMPLETED
        || status == AthenaJobStatus.FAILED
        || status == AthenaJobStatus.CANCELLED;
  }

  private AthenaJobStatus mapAthenaStateToJobStatus(QueryExecutionState state) {
    return switch (state) {
      case QUEUED, RUNNING -> AthenaJobStatus.RUNNING;
      case SUCCEEDED -> AthenaJobStatus.COMPLETED;
      case FAILED -> AthenaJobStatus.FAILED;
      case CANCELLED -> AthenaJobStatus.CANCELLED;
      default -> AthenaJobStatus.SUBMITTED;
    };
  }
}

package org.dreamhorizon.pulseserver.service.athena;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.athena.AthenaClient;
import org.dreamhorizon.pulseserver.dao.athena.AthenaJobDao;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;
import org.dreamhorizon.pulseserver.util.QueryTimestampEnricher;
import org.dreamhorizon.pulseserver.util.SqlQueryValidator;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.Row;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AthenaService {
  private final AthenaClient athenaClient;
  private final AthenaJobDao athenaJobDao;

  public Single<AthenaJob> submitQuery(String queryString, List<String> parameters, String timestampString) {
    SqlQueryValidator.ValidationResult validation = SqlQueryValidator.validateQuery(queryString);
    if (!validation.isValid()) {
      return Single.error(new IllegalArgumentException("Invalid SQL query: " + validation.getErrorMessage()));
    }

    final String enrichedQuery = (timestampString != null && !timestampString.trim().isEmpty())
        ? QueryTimestampEnricher.enrichQueryWithTimestamp(queryString, timestampString)
        : queryString;

    if (timestampString != null && !timestampString.trim().isEmpty()) {
      log.debug("Enriched query with timestamp filters: {}", enrichedQuery);
    }

    return athenaJobDao.createJob(enrichedQuery)
        .flatMap(jobId -> athenaClient.submitQuery(enrichedQuery, parameters)
            .flatMap(queryExecutionId -> athenaClient.getQueryExecution(queryExecutionId)
                .flatMap(execution -> {
                  Long dataScannedBytes = execution.statistics() != null
                      ? execution.statistics().dataScannedInBytes()
                      : null;

                  return athenaJobDao.updateJobWithExecutionId(jobId, queryExecutionId, AthenaJobStatus.RUNNING)
                      .flatMap(result -> waitForCompletionWithTimeout(queryExecutionId, 3, TimeUnit.SECONDS)
                          .flatMap(state -> {
                            if (state == QueryExecutionState.SUCCEEDED) {
                              log.info("Query completed within 3 seconds for job: {}", jobId);
                              return fetchResultsForJob(jobId, queryExecutionId, dataScannedBytes);
                            } else if (state == QueryExecutionState.FAILED || state == QueryExecutionState.CANCELLED) {
                              log.warn("Query failed within 3 seconds for job: {}, state: {}", jobId, state);
                              return athenaClient.getQueryExecution(queryExecutionId)
                                  .flatMap(failedExecution -> {
                                    String errorMessage = failedExecution.status().stateChangeReason() != null
                                        ? failedExecution.status().stateChangeReason()
                                        : "Query " + state.name().toLowerCase();
                                    return athenaJobDao.updateJobFailed(jobId, errorMessage)
                                        .flatMap(v -> athenaJobDao.getJobById(jobId)
                                            .map(job -> buildJobWithDataScanned(job, dataScannedBytes)));
                                  });
                            } else {
                              log.debug("Query still running after 3 seconds for job: {}, returning job ID only", jobId);
                              return athenaJobDao.getJobById(jobId)
                                  .map(job -> buildJobWithDataScanned(job, dataScannedBytes));
                            }
                          })
                          .onErrorResumeNext(error -> {
                            log.debug("Error or timeout waiting for query completion for job: {}, returning job ID only", jobId);
                            return athenaJobDao.getJobById(jobId)
                                .map(job -> buildJobWithDataScanned(job, dataScannedBytes));
                          }));
                }))
            .onErrorResumeNext(error -> {
              log.error("Error submitting query to Athena for job: {}", jobId, error);
              return athenaJobDao.updateJobFailed(jobId, "Failed to submit query: " + error.getMessage())
                  .flatMap(v -> Single.error(error));
            }));
  }

  private Single<QueryExecutionState> waitForCompletionWithTimeout(String queryExecutionId, long timeout, TimeUnit unit) {
    long startTime = System.currentTimeMillis();

    return athenaClient.getQueryStatus(queryExecutionId)
        .flatMap(initialState -> {
          if (isQueryExecutionStateFinal(initialState)) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Query already in final state: {} (checked in {}ms)", initialState, elapsed);
            return Single.just(initialState);
          }

          long elapsed = System.currentTimeMillis() - startTime;
          long remainingTimeout = unit.toMillis(timeout) - elapsed;

          if (remainingTimeout <= 0) {
            log.debug("Timeout already exceeded after initial check, returning current state: {}", initialState);
            return Single.just(initialState);
          }

          log.debug("Query not in final state yet ({}), starting to poll every 200ms for up to {}ms",
              initialState, remainingTimeout);

          return Observable.interval(200, TimeUnit.MILLISECONDS)
              .flatMapSingle(tick -> athenaClient.getQueryStatus(queryExecutionId))
              .filter(this::isQueryExecutionStateFinal)
              .firstOrError()
              .timeout(remainingTimeout, TimeUnit.MILLISECONDS)
              .onErrorResumeNext(error -> {
                long totalElapsed = System.currentTimeMillis() - startTime;
                log.debug("Timeout waiting for query completion after {}ms (requested {} {}), checking current status",
                    totalElapsed, timeout, unit);
                return athenaClient.getQueryStatus(queryExecutionId);
              });
        });
  }

  private Single<AthenaJob> fetchResultsForJob(String jobId, String queryExecutionId, Long dataScannedBytes) {
    return athenaClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String resultLocation = execution.resultConfiguration() != null
              ? execution.resultConfiguration().outputLocation()
              : null;

          return athenaJobDao.updateJobCompleted(jobId, resultLocation)
              .flatMap(v -> Single.timer(200, TimeUnit.MILLISECONDS)
                  .flatMap(tick -> fetchResultsWithRetry(queryExecutionId, 5, 300)
                      .flatMap(resultSetWithToken -> {
                        JsonArray resultData = convertResultSetToJsonArray(resultSetWithToken.getResultSet());
                        log.info("Successfully fetched {} result rows for job: {}", resultData.size(), jobId);

                        return athenaJobDao.getJobById(jobId)
                            .map(job -> AthenaJob.builder()
                                .jobId(job.getJobId())
                                .queryString(job.getQueryString())
                                .queryExecutionId(job.getQueryExecutionId())
                                .status(AthenaJobStatus.COMPLETED)
                                .resultLocation(job.getResultLocation())
                                .errorMessage(job.getErrorMessage())
                                .resultData(resultData)
                                .nextToken(resultSetWithToken.getNextToken())
                                .dataScannedInBytes(dataScannedBytes)
                                .createdAt(job.getCreatedAt())
                                .updatedAt(job.getUpdatedAt())
                                .completedAt(job.getCompletedAt())
                                .build());
                      })));
        })
        .onErrorResumeNext(error -> {
          log.error("Error fetching results for job: {} after retries. Error: {}", jobId, error.getMessage(), error);
          return athenaJobDao.getJobById(jobId)
              .map(job -> AthenaJob.builder()
                  .jobId(job.getJobId())
                  .queryString(job.getQueryString())
                  .queryExecutionId(job.getQueryExecutionId())
                  .status(AthenaJobStatus.COMPLETED)
                  .resultLocation(job.getResultLocation())
                  .errorMessage(job.getErrorMessage())
                  .resultData(null)
                  .dataScannedInBytes(dataScannedBytes)
                  .createdAt(job.getCreatedAt())
                  .updatedAt(job.getUpdatedAt())
                  .completedAt(job.getCompletedAt())
                  .build());
        });
  }

  private Single<org.dreamhorizon.pulseserver.client.athena.models.ResultSetWithToken> fetchResultsWithRetry(
      String queryExecutionId, int maxRetries, long delayMs) {
    return athenaClient.getQueryResults(queryExecutionId, 1000, null)
        .onErrorResumeNext(error -> {
          if (maxRetries > 0) {
            log.warn("Failed to fetch results for query {}: {}. Retrying in {}ms ({} retries left)",
                queryExecutionId, error.getMessage(), delayMs, maxRetries);
            return Single.timer(delayMs, TimeUnit.MILLISECONDS)
                .flatMap(tick -> {
                  log.debug("Retrying to fetch results for query: {}", queryExecutionId);
                  return fetchResultsWithRetry(queryExecutionId, maxRetries - 1, delayMs);
                });
          } else {
            log.error("Failed to fetch results for query {} after all retries. Last error: {}",
                queryExecutionId, error.getMessage(), error);
            return Single.error(error);
          }
        });
  }

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
                  if (newStatus == AthenaJobStatus.COMPLETED) {
                    return fetchAndUpdateJobResults(jobId, job.getQueryExecutionId())
                        .flatMap(updatedJob -> {
                          if (maxResults != null || nextToken != null) {
                            return fetchPaginatedResults(updatedJob, maxResults, nextToken);
                          }
                          return Single.just(updatedJob);
                        });
                  } else if (newStatus == AthenaJobStatus.FAILED || newStatus == AthenaJobStatus.CANCELLED) {
                    return athenaClient.getQueryExecution(job.getQueryExecutionId())
                        .flatMap(execution -> {
                          String errorMessage = execution.status().stateChangeReason() != null
                              ? execution.status().stateChangeReason()
                              : "Query " + newStatus.name().toLowerCase();
                          return athenaJobDao.updateJobFailed(jobId, errorMessage)
                              .flatMap(v -> athenaJobDao.getJobById(jobId));
                        });
                  } else {
                    return athenaJobDao.updateJobStatus(jobId, newStatus)
                        .flatMap(v -> athenaJobDao.getJobById(jobId));
                  }
                }

                if (newStatus == AthenaJobStatus.COMPLETED && (maxResults != null || nextToken != null)) {
                  return fetchPaginatedResults(job, maxResults, nextToken);
                }

                return Single.just(job);
              });
        });
  }

  private Single<AthenaJob> fetchPaginatedResults(AthenaJob job, Integer maxResults, String nextToken) {
    if (job.getQueryExecutionId() == null) {
      return Single.just(job);
    }

    Integer athenaMaxResults = maxResults != null ? maxResults + 1 : null;

    if (athenaMaxResults != null && athenaMaxResults > 1000) {
      athenaMaxResults = 1000;
    }

    return athenaClient.getQueryResults(job.getQueryExecutionId(), athenaMaxResults, nextToken)
        .map(resultSetWithToken -> {
          JsonArray resultData = convertResultSetToJsonArray(resultSetWithToken.getResultSet());

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
              .nextToken(resultSetWithToken.getNextToken())
              .dataScannedInBytes(job.getDataScannedInBytes())
              .build();
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
                  return athenaClient.getQueryExecution(job.getQueryExecutionId())
                      .flatMap(execution -> {
                        String errorMessage = execution.status().stateChangeReason() != null
                            ? execution.status().stateChangeReason()
                            : "Query " + state.name().toLowerCase();
                        return athenaJobDao.updateJobFailed(jobId, errorMessage)
                            .flatMap(v -> athenaJobDao.getJobById(jobId));
                      });
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

          return athenaJobDao.updateJobCompleted(jobId, resultLocation)
              .flatMap(v -> athenaJobDao.getJobById(jobId));
        })
        .onErrorResumeNext(error -> {
          log.error("Error updating job results location for job: {}", jobId, error);
          return athenaJobDao.updateJobFailed(jobId, "Failed to update result location: " + error.getMessage())
              .flatMap(v -> athenaJobDao.getJobById(jobId));
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

  private boolean isQueryExecutionStateFinal(QueryExecutionState state) {
    return state == QueryExecutionState.SUCCEEDED
        || state == QueryExecutionState.FAILED
        || state == QueryExecutionState.CANCELLED;
  }

  private boolean isFinalState(AthenaJobStatus status) {
    return status == AthenaJobStatus.COMPLETED
        || status == AthenaJobStatus.FAILED
        || status == AthenaJobStatus.CANCELLED;
  }

  private AthenaJobStatus mapAthenaStateToJobStatus(QueryExecutionState state) {
    switch (state) {
      case QUEUED:
      case RUNNING:
        return AthenaJobStatus.RUNNING;
      case SUCCEEDED:
        return AthenaJobStatus.COMPLETED;
      case FAILED:
        return AthenaJobStatus.FAILED;
      case CANCELLED:
        return AthenaJobStatus.CANCELLED;
      default:
        return AthenaJobStatus.SUBMITTED;
    }
  }
}

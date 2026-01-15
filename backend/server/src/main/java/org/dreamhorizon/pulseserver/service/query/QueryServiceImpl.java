package org.dreamhorizon.pulseserver.service.query;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.dreamhorizon.pulseserver.client.query.QueryClient;
import org.dreamhorizon.pulseserver.client.query.models.QueryResultSet;
import org.dreamhorizon.pulseserver.client.query.models.QueryStatus;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.service.query.models.ColumnMetadata;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.dreamhorizon.pulseserver.service.query.models.TableMetadata;
import org.dreamhorizon.pulseserver.util.SqlQueryValidator;
import java.sql.Timestamp;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class QueryServiceImpl implements QueryService {

  private final QueryClient queryClient;
  private final QueryJobDao queryJobDao;
  private final AthenaConfig athenaConfig;

  @Override
  public Single<QueryJob> submitQuery(String queryString, String userEmail) {
    if (userEmail == null || userEmail.trim().isEmpty()) {
      return Single.error(new IllegalArgumentException("User email is required and cannot be null or empty"));
    }

    SqlQueryValidator.ValidationResult validationResult = SqlQueryValidator.validateQuery(queryString);
    if (!validationResult.isValid()) {
      return Single.error(new IllegalArgumentException(validationResult.getErrorMessage()));
    }

    return queryJobDao.createJob(queryString, userEmail.trim())
        .flatMap(jobId -> queryClient.submitQuery(queryString)
            .flatMap(queryExecutionId -> queryClient.getQueryExecution(queryExecutionId)
                .flatMap(execution -> {
                  Long initialDataScannedBytes = execution.getDataScannedInBytes();
                  Timestamp submissionDateTime = execution.getSubmissionDateTime();

                  return queryJobDao.updateJobWithExecutionId(jobId, queryExecutionId, QueryJobStatus.RUNNING, submissionDateTime)
                      .flatMap(result -> waitForCompletionWithTimeout(queryExecutionId, Constants.QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                          .flatMap(status -> handleQueryState(jobId, queryExecutionId, status, initialDataScannedBytes))
                          .onErrorResumeNext(error -> {
                            log.debug("Error or timeout waiting for query completion for job: {}, returning job ID only", jobId);
                            return queryJobDao.getJobById(jobId)
                                .map(job -> buildJobWithDataScanned(job, initialDataScannedBytes));
                          }));
                }))
            .onErrorResumeNext(error -> {
              log.error("Error submitting query for job: {}", jobId, error);
              return queryJobDao.updateJobFailed(jobId, "Failed to submit query: " + error.getMessage(), null)
                  .flatMap(v -> Single.error(error));
            }));
  }

  private Single<QueryJob> handleQueryState(String jobId, String queryExecutionId, QueryStatus status, Long initialDataScannedBytes) {
    if (status == QueryStatus.SUCCEEDED) {
      log.info("Query completed within {} seconds for job: {}", Constants.QUERY_TIMEOUT_SECONDS, jobId);
      return fetchResultsForJob(jobId, queryExecutionId, initialDataScannedBytes);
    } else if (status == QueryStatus.FAILED || status == QueryStatus.CANCELLED) {
      log.warn("Query failed within {} seconds for job: {}, status: {}", Constants.QUERY_TIMEOUT_SECONDS, jobId, status);
      return handleFailedQuery(jobId, queryExecutionId, status, initialDataScannedBytes);
    } else {
      log.debug("Query still running after {} seconds for job: {}, returning job ID only", Constants.QUERY_TIMEOUT_SECONDS, jobId);
      return queryJobDao.getJobById(jobId)
          .map(job -> buildJobWithDataScanned(job, initialDataScannedBytes));
    }
  }

  private Single<QueryJob> handleFailedQuery(String jobId, String queryExecutionId, QueryStatus status, Long fallbackDataScannedBytes) {
    return queryClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String errorMessage = execution.getStateChangeReason() != null
              ? execution.getStateChangeReason()
              : "Query " + status.name().toLowerCase();
          Long dataScannedBytes = execution.getDataScannedInBytes() != null
              ? execution.getDataScannedInBytes()
              : fallbackDataScannedBytes;
          Timestamp completionDateTime = execution.getCompletionDateTime();
          return queryJobDao.updateJobFailed(jobId, errorMessage, completionDateTime)
              .flatMap(v -> queryJobDao.updateJobStatistics(jobId, dataScannedBytes,
                  execution.getExecutionTimeMillis(), execution.getEngineExecutionTimeMillis(), execution.getQueryQueueTimeMillis(), completionDateTime))
              .flatMap(v -> queryJobDao.getJobById(jobId));
        });
  }

  private Single<QueryStatus> waitForCompletionWithTimeout(String queryExecutionId, long timeout, TimeUnit unit) {
    long startTime = System.currentTimeMillis();

    return queryClient.getQueryStatus(queryExecutionId)
        .flatMap(initialStatus -> {
          if (isQueryStatusFinal(initialStatus)) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Query already in final state: {} (checked in {}ms)", initialStatus, elapsed);
            return Single.just(initialStatus);
          }

          long elapsed = System.currentTimeMillis() - startTime;
          long remainingTimeout = unit.toMillis(timeout) - elapsed;

          if (remainingTimeout <= 0) {
            log.debug("Timeout already exceeded after initial check, returning current state: {}", initialStatus);
            return Single.just(initialStatus);
          }

          log.debug("Query not in final state yet ({}), starting to poll every {}ms for up to {}ms",
              initialStatus, Constants.QUERY_POLL_INTERVAL_MS, remainingTimeout);

          return Observable.interval(Constants.QUERY_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
              .flatMapSingle(tick -> queryClient.getQueryStatus(queryExecutionId))
              .filter(this::isQueryStatusFinal)
              .firstOrError()
              .timeout(remainingTimeout, TimeUnit.MILLISECONDS)
              .onErrorResumeNext(error -> {
                long totalElapsed = System.currentTimeMillis() - startTime;
                log.debug("Timeout waiting for query completion after {}ms (requested {} {}), checking current status",
                    totalElapsed, timeout, unit);
                return queryClient.getQueryStatus(queryExecutionId);
              });
        });
  }

  private Single<QueryJob> fetchResultsForJob(String jobId, String queryExecutionId, Long fallbackDataScannedBytes) {
    return queryClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String resultLocation = execution.getResultLocation();
          Long dataScannedBytes = execution.getDataScannedInBytes() != null
              ? execution.getDataScannedInBytes()
              : fallbackDataScannedBytes;
          Timestamp completionDateTime = execution.getCompletionDateTime();

          return queryJobDao.updateJobCompleted(jobId, resultLocation, completionDateTime)
              .flatMap(v -> queryJobDao.updateJobStatistics(jobId, dataScannedBytes,
                  execution.getExecutionTimeMillis(), execution.getEngineExecutionTimeMillis(), execution.getQueryQueueTimeMillis(), completionDateTime))
              .flatMap(v -> Single.timer(Constants.RESULT_FETCH_DELAY_MS, TimeUnit.MILLISECONDS)
                  .flatMap(tick -> fetchResultsWithRetry(queryExecutionId, Constants.MAX_RESULT_FETCH_RETRIES, Constants.RESULT_FETCH_RETRY_DELAY_MS)
                      .flatMap(resultSet -> {
                        log.info("Successfully fetched {} result rows for job: {}", 
                            resultSet.getResultData() != null ? resultSet.getResultData().size() : 0, jobId);

                        return queryJobDao.getJobById(jobId)
                            .map(job -> buildCompletedJob(job, resultSet.getResultData(), resultSet.getNextToken(), dataScannedBytes));
                      })));
        })
        .onErrorResumeNext(error -> {
          log.error("Error fetching results for job: {} after retries. Error: {}", jobId, error.getMessage(), error);
          return queryClient.getQueryExecution(queryExecutionId)
              .flatMap(execution -> {
                Long dataScannedBytes = execution.getDataScannedInBytes() != null
                    ? execution.getDataScannedInBytes()
                    : fallbackDataScannedBytes;
                Timestamp completionDateTime = execution.getCompletionDateTime();
                return queryJobDao.updateJobStatistics(jobId, dataScannedBytes,
                    execution.getExecutionTimeMillis(), execution.getEngineExecutionTimeMillis(), execution.getQueryQueueTimeMillis(), completionDateTime)
                    .flatMap(v -> queryJobDao.getJobById(jobId))
                    .map(job -> buildCompletedJob(job, null, null, dataScannedBytes));
              })
              .onErrorResumeNext(fallbackError -> queryJobDao.getJobById(jobId)
                  .map(job -> buildCompletedJob(job, null, null, fallbackDataScannedBytes)));
        });
  }

  private Single<org.dreamhorizon.pulseserver.client.query.models.QueryResultSet> fetchResultsWithRetry(
      String queryExecutionId, int maxRetries, long delayMs) {
    return queryClient.getQueryResults(queryExecutionId, Constants.MAX_QUERY_RESULTS, null)
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

  @Override
  public Single<QueryJob> getJobStatus(String jobId, Integer maxResults, String nextToken) {
    return queryJobDao.getJobById(jobId)
        .flatMap(job -> {
          if (job == null) {
            return Single.error(new RuntimeException("Job not found: " + jobId));
          }

          if (isFinalState(job.getStatus())) {
            if (job.getStatus() == QueryJobStatus.COMPLETED && job.getQueryExecutionId() != null) {
              // If statistics are missing, refresh them from AWS
              if (job.getDataScannedInBytes() == null || job.getExecutionTimeMillis() == null) {
                log.debug("Statistics missing for completed job {}, refreshing from AWS", jobId);
                return fetchAndUpdateJobResults(jobId, job.getQueryExecutionId())
                    .flatMap(updatedJob -> {
                      if (maxResults != null || nextToken != null) {
                        return fetchPaginatedResults(updatedJob, maxResults, nextToken);
                      }
                      return Single.just(updatedJob);
                    });
              }
              return fetchPaginatedResults(job, maxResults, nextToken);
            }
            return Single.just(job);
          }

          if (job.getQueryExecutionId() == null) {
            return Single.just(job);
          }

          return queryClient.getQueryStatus(job.getQueryExecutionId())
              .flatMap(status -> {
                QueryJobStatus newStatus = mapQueryStatusToJobStatus(status);

                if (newStatus != job.getStatus()) {
                  return handleStatusChange(jobId, job.getQueryExecutionId(), newStatus, job, maxResults, nextToken);
                }

                if (newStatus == QueryJobStatus.COMPLETED && (maxResults != null || nextToken != null)) {
                  return fetchPaginatedResults(job, maxResults, nextToken);
                }

                return Single.just(job);
              });
        });
  }

  private Single<QueryJob> handleStatusChange(String jobId, String queryExecutionId, QueryJobStatus newStatus,
      QueryJob job, Integer maxResults, String nextToken) {
    if (newStatus == QueryJobStatus.COMPLETED) {
      return fetchAndUpdateJobResults(jobId, queryExecutionId)
          .flatMap(updatedJob -> {
            if (maxResults != null || nextToken != null) {
              return fetchPaginatedResults(updatedJob, maxResults, nextToken);
            }
            return Single.just(updatedJob);
          });
    } else if (newStatus == QueryJobStatus.FAILED || newStatus == QueryJobStatus.CANCELLED) {
      return handleFailedQueryInStatusCheck(jobId, queryExecutionId, newStatus);
    } else {
      return queryClient.getQueryExecution(queryExecutionId)
          .flatMap(execution -> {
            Timestamp updatedAt = execution.getCompletionDateTime() != null 
                ? execution.getCompletionDateTime() 
                : execution.getSubmissionDateTime();
            return queryJobDao.updateJobStatus(jobId, newStatus, updatedAt)
                .flatMap(v -> queryJobDao.getJobById(jobId));
          });
    }
  }

  private Single<QueryJob> handleFailedQueryInStatusCheck(String jobId, String queryExecutionId, QueryJobStatus status) {
    return queryClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String errorMessage = execution.getStateChangeReason() != null
              ? execution.getStateChangeReason()
              : "Query " + status.name().toLowerCase();
          Long dataScannedBytes = execution.getDataScannedInBytes();
          Timestamp completionDateTime = execution.getCompletionDateTime();
          return queryJobDao.updateJobFailed(jobId, errorMessage, completionDateTime)
              .flatMap(v -> queryJobDao.updateJobStatistics(jobId, dataScannedBytes,
                  execution.getExecutionTimeMillis(), execution.getEngineExecutionTimeMillis(), execution.getQueryQueueTimeMillis(), completionDateTime))
              .flatMap(v -> queryJobDao.getJobById(jobId));
        });
  }

  private Single<QueryJob> fetchPaginatedResults(QueryJob job, Integer maxResults, String nextToken) {
    if (job.getQueryExecutionId() == null) {
      return Single.just(job);
    }

    Integer queryMaxResults = maxResults != null ? Math.min(maxResults + 1, Constants.MAX_QUERY_RESULTS) : null;

    return queryClient.getQueryResults(job.getQueryExecutionId(), queryMaxResults, nextToken)
        .map(resultSet -> {
          return buildJobWithResults(job, resultSet.getResultData(), resultSet.getNextToken());
        });
  }

  @Override
  public Single<QueryJob> waitForJobCompletion(String jobId) {
    return queryJobDao.getJobById(jobId)
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

          return queryClient.waitForQueryCompletion(job.getQueryExecutionId())
              .flatMap(status -> {
                if (status == QueryStatus.SUCCEEDED) {
                  return fetchAndUpdateJobResults(jobId, job.getQueryExecutionId());
                } else {
                  return handleFailedQueryInStatusCheck(jobId, job.getQueryExecutionId(),
                      mapQueryStatusToJobStatus(status));
                }
              });
        });
  }

  private Single<QueryJob> fetchAndUpdateJobResults(String jobId, String queryExecutionId) {
    return queryClient.getQueryExecution(queryExecutionId)
        .flatMap(execution -> {
          String resultLocation = execution.getResultLocation();
          Long dataScannedBytes = execution.getDataScannedInBytes();
          Timestamp completionDateTime = execution.getCompletionDateTime();

          return queryJobDao.updateJobCompleted(jobId, resultLocation, completionDateTime)
              .flatMap(v -> queryJobDao.updateJobStatistics(jobId, dataScannedBytes,
                  execution.getExecutionTimeMillis(), execution.getEngineExecutionTimeMillis(), execution.getQueryQueueTimeMillis(), completionDateTime))
              .flatMap(v -> queryJobDao.getJobById(jobId));
        })
        .onErrorResumeNext(error -> {
          log.error("Error updating job results location for job: {}", jobId, error);
          return queryClient.getQueryExecution(queryExecutionId)
              .flatMap(execution -> {
                Timestamp completionDateTime = execution.getCompletionDateTime();
                Long dataScannedBytes = execution.getDataScannedInBytes();
                return queryJobDao.updateJobFailed(jobId, "Failed to update result location: " + error.getMessage(), completionDateTime)
                    .flatMap(v -> queryJobDao.updateJobStatistics(jobId, dataScannedBytes,
                        execution.getExecutionTimeMillis(), execution.getEngineExecutionTimeMillis(), execution.getQueryQueueTimeMillis(), completionDateTime))
                    .flatMap(v -> queryJobDao.getJobById(jobId));
              })
              .onErrorResumeNext(fallbackError -> {
                return queryJobDao.updateJobFailed(jobId, "Failed to update result location: " + error.getMessage(), null)
                    .flatMap(v -> queryJobDao.getJobById(jobId));
              });
        });
  }

  private QueryJob buildJobWithDataScanned(QueryJob job, Long dataScannedBytes) {
    return QueryJob.builder()
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

  private QueryJob buildCompletedJob(QueryJob job, io.vertx.core.json.JsonArray resultData, String nextToken, Long dataScannedBytes) {
    return QueryJob.builder()
        .jobId(job.getJobId())
        .queryString(job.getQueryString())
        .queryExecutionId(job.getQueryExecutionId())
        .status(QueryJobStatus.COMPLETED)
        .resultLocation(job.getResultLocation())
        .errorMessage(job.getErrorMessage())
        .resultData(resultData)
        .nextToken(nextToken)
        .dataScannedInBytes(dataScannedBytes)
        .createdAt(job.getCreatedAt())
        .updatedAt(job.getUpdatedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }

  private QueryJob buildJobWithResults(QueryJob job, io.vertx.core.json.JsonArray resultData, String nextToken) {
    return QueryJob.builder()
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

  private boolean isQueryStatusFinal(QueryStatus status) {
    return status == QueryStatus.SUCCEEDED
        || status == QueryStatus.FAILED
        || status == QueryStatus.CANCELLED;
  }

  private boolean isFinalState(QueryJobStatus status) {
    return status == QueryJobStatus.COMPLETED
        || status == QueryJobStatus.FAILED
        || status == QueryJobStatus.CANCELLED;
  }

  @Override
  public Single<QueryJob> cancelQuery(String jobId) {
    return queryJobDao.getJobById(jobId)
        .flatMap(job -> {
          if (job == null) {
            return Single.error(new RuntimeException("Job not found: " + jobId));
          }

          if (isFinalState(job.getStatus())) {
            log.warn("Cannot cancel job {} - already in final state: {}", jobId, job.getStatus());
            return Single.just(job);
          }

          if (job.getQueryExecutionId() == null) {
            log.warn("Cannot cancel job {} - no query execution ID available", jobId);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, now)
                .flatMap(v -> queryJobDao.getJobById(jobId));
          }

          return queryClient.cancelQuery(job.getQueryExecutionId())
              .flatMap(cancelled -> {
                if (cancelled) {
                  log.info("Successfully cancelled query execution {} for job {}", job.getQueryExecutionId(), jobId);
                  return queryClient.getQueryExecution(job.getQueryExecutionId())
                      .flatMap(execution -> {
                        Timestamp updatedAt = execution.getCompletionDateTime() != null 
                            ? execution.getCompletionDateTime() 
                            : new Timestamp(System.currentTimeMillis());
                        return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, updatedAt)
                            .flatMap(v -> queryJobDao.getJobById(jobId));
                      })
                      .onErrorResumeNext(error -> {
                        Timestamp now = new Timestamp(System.currentTimeMillis());
                        return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, now)
                            .flatMap(v -> queryJobDao.getJobById(jobId));
                      });
                } else {
                  log.warn("Failed to cancel query execution {} for job {}", job.getQueryExecutionId(), jobId);
                  return queryClient.getQueryExecution(job.getQueryExecutionId())
                      .flatMap(execution -> {
                        QueryJobStatus newStatus = mapQueryStatusToJobStatus(execution.getStatus());
                        Timestamp updatedAt = execution.getCompletionDateTime() != null 
                            ? execution.getCompletionDateTime() 
                            : (execution.getSubmissionDateTime() != null ? execution.getSubmissionDateTime() : new Timestamp(System.currentTimeMillis()));
                        if (newStatus == QueryJobStatus.CANCELLED) {
                          return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, updatedAt)
                              .flatMap(v -> queryJobDao.getJobById(jobId));
                        }
                        return Single.error(new RuntimeException("Failed to cancel query execution"));
                      });
                }
              })
              .onErrorResumeNext(error -> {
                log.error("Error cancelling query for job: {}", jobId, error);
                return queryClient.getQueryExecution(job.getQueryExecutionId())
                    .flatMap(execution -> {
                      QueryJobStatus newStatus = mapQueryStatusToJobStatus(execution.getStatus());
                      Timestamp updatedAt = execution.getCompletionDateTime() != null 
                          ? execution.getCompletionDateTime() 
                          : (execution.getSubmissionDateTime() != null ? execution.getSubmissionDateTime() : new Timestamp(System.currentTimeMillis()));
                      if (newStatus == QueryJobStatus.CANCELLED) {
                        return queryJobDao.updateJobStatus(jobId, QueryJobStatus.CANCELLED, updatedAt)
                            .flatMap(v -> queryJobDao.getJobById(jobId));
                      }
                      return Single.error(error);
                    })
                    .onErrorResumeNext(fallbackError -> Single.error(
                        new RuntimeException("Failed to cancel query: " + error.getMessage(), error)));
              });
        });
  }

  private QueryJobStatus mapQueryStatusToJobStatus(QueryStatus status) {
    switch (status) {
      case QUEUED:
      case RUNNING:
        return QueryJobStatus.RUNNING;
      case SUCCEEDED:
        return QueryJobStatus.COMPLETED;
      case FAILED:
        return QueryJobStatus.FAILED;
      case CANCELLED:
        return QueryJobStatus.CANCELLED;
      default:
        return QueryJobStatus.SUBMITTED;
    }
  }

  @Override
  public Single<List<QueryJob>> getQueryHistory(String userEmail, Integer limit, Integer offset) {
    if (userEmail == null || userEmail.trim().isEmpty()) {
      return Single.error(new IllegalArgumentException("User email is required and cannot be null or empty"));
    }

    int queryLimit = limit != null && limit > 0 ? Math.min(limit, Constants.MAX_QUERY_RESULTS) : 20;
    int queryOffset = offset != null && offset >= 0 ? offset : 0;

    return queryJobDao.getQueryHistory(userEmail.trim(), queryLimit, queryOffset)
        .flatMap(jobs -> {
          // Check for running jobs that need status updates
          List<QueryJob> runningJobs = jobs.stream()
              .filter(job -> job.getStatus() == QueryJobStatus.RUNNING && job.getQueryExecutionId() != null)
              .limit(20)
              .collect(java.util.stream.Collectors.toList());

          // Check for completed jobs with missing statistics that need to be refreshed
          List<QueryJob> completedJobsWithMissingStats = jobs.stream()
              .filter(job -> job.getStatus() == QueryJobStatus.COMPLETED 
                  && job.getQueryExecutionId() != null
                  && (job.getDataScannedInBytes() == null || job.getExecutionTimeMillis() == null))
              .limit(20)
              .collect(java.util.stream.Collectors.toList());

          if (runningJobs.isEmpty() && completedJobsWithMissingStats.isEmpty()) {
            return Single.just(jobs);
          }

          List<QueryJob> jobsToUpdate = new java.util.ArrayList<>();
          jobsToUpdate.addAll(runningJobs);
          jobsToUpdate.addAll(completedJobsWithMissingStats);

          log.debug("Checking status for {} running queries and {} completed queries with missing statistics", 
              runningJobs.size(), completedJobsWithMissingStats.size());

          return Observable.fromIterable(jobsToUpdate)
              .flatMapSingle(job -> {
                if (job.getStatus() == QueryJobStatus.RUNNING) {
                  return checkAndUpdateRunningJob(job)
                      .onErrorReturn(error -> {
                        log.warn("Error checking status for job {}: {}", job.getJobId(), error.getMessage());
                        return job;
                      });
                } else if (job.getStatus() == QueryJobStatus.COMPLETED) {
                  // Refresh statistics for completed jobs with missing stats
                  return fetchAndUpdateJobResults(job.getJobId(), job.getQueryExecutionId())
                      .onErrorReturn(error -> {
                        log.warn("Error refreshing statistics for completed job {}: {}", job.getJobId(), error.getMessage());
                        return job;
                      });
                }
                return Single.just(job);
              })
              .toList()
              .map(updatedJobs -> {
                java.util.Map<String, QueryJob> updatedMap = new java.util.HashMap<>();
                updatedJobs.forEach(updatedJob -> updatedMap.put(updatedJob.getJobId(), updatedJob));

                return jobs.stream()
                    .map(job -> {
                      if (updatedMap.containsKey(job.getJobId())) {
                        return updatedMap.get(job.getJobId());
                      }
                      return job;
                    })
                    .collect(java.util.stream.Collectors.toList());
              });
        });
  }

  private Single<QueryJob> checkAndUpdateRunningJob(QueryJob job) {
    return queryClient.getQueryStatus(job.getQueryExecutionId())
        .flatMap(status -> {
          QueryJobStatus newStatus = mapQueryStatusToJobStatus(status);

          if (newStatus == job.getStatus()) {
            return Single.just(job);
          }

          if (newStatus == QueryJobStatus.COMPLETED) {
            return fetchAndUpdateJobResults(job.getJobId(), job.getQueryExecutionId());
          } else if (newStatus == QueryJobStatus.FAILED || newStatus == QueryJobStatus.CANCELLED) {
            return handleFailedQueryInStatusCheck(job.getJobId(), job.getQueryExecutionId(), newStatus);
          } else {
            return queryClient.getQueryExecution(job.getQueryExecutionId())
                .flatMap(execution -> {
                  Timestamp updatedAt = execution.getCompletionDateTime() != null 
                      ? execution.getCompletionDateTime() 
                      : execution.getSubmissionDateTime();
                  if (updatedAt == null) {
                    updatedAt = new Timestamp(System.currentTimeMillis());
                  }
                  return queryJobDao.updateJobStatus(job.getJobId(), newStatus, updatedAt)
                      .flatMap(v -> queryJobDao.getJobById(job.getJobId()));
                });
          }
        });
  }

  @Override
  public Single<List<TableMetadata>> getTablesAndColumns() {
    String database = athenaConfig.getDatabase();
    if (database == null || database.trim().isEmpty()) {
      return Single.error(new IllegalArgumentException("Database name is not configured"));
    }

    String tablesQuery = String.format(
        "SELECT table_schema, table_name, table_type " +
        "FROM information_schema.tables " +
        "WHERE table_schema = '%s' " +
        "ORDER BY table_name",
        database
    );

    String columnsQuery = String.format(
        "SELECT table_schema, table_name, column_name, data_type, ordinal_position, is_nullable " +
        "FROM information_schema.columns " +
        "WHERE table_schema = '%s' " +
        "ORDER BY table_name, ordinal_position",
        database
    );

    return queryClient.submitQuery(tablesQuery)
        .flatMap(queryExecutionId -> queryClient.waitForQueryCompletion(queryExecutionId)
            .flatMap(status -> {
              if (status == QueryStatus.SUCCEEDED) {
                return queryClient.getQueryResults(queryExecutionId, null, null)
                    .map(QueryResultSet::getResultData);
              } else {
                return Single.error(new RuntimeException("Failed to query tables: " + status));
              }
            }))
        .flatMap(tablesResult -> {
          return queryClient.submitQuery(columnsQuery)
              .flatMap(queryExecutionId -> queryClient.waitForQueryCompletion(queryExecutionId)
                  .flatMap(status -> {
                    if (status == QueryStatus.SUCCEEDED) {
                      return queryClient.getQueryResults(queryExecutionId, null, null)
                          .map(QueryResultSet::getResultData);
                    } else {
                      return Single.error(new RuntimeException("Failed to query columns: " + status));
                    }
                  }))
              .map(columnsResult -> combineTablesAndColumns(tablesResult, columnsResult));
        });
  }

  private List<TableMetadata> combineTablesAndColumns(JsonArray tablesResult, JsonArray columnsResult) {
    Map<String, TableMetadata> tableMap = new HashMap<>();

    for (int i = 0; i < tablesResult.size(); i++) {
      JsonObject tableRow = tablesResult.getJsonObject(i);
      String tableName = tableRow.getString("table_name");
      String tableSchema = tableRow.getString("table_schema");
      String tableType = tableRow.getString("table_type");

      TableMetadata table = TableMetadata.builder()
          .tableName(tableName)
          .tableSchema(tableSchema)
          .tableType(tableType)
          .columns(new ArrayList<>())
          .build();

      tableMap.put(tableName, table);
    }

    for (int i = 0; i < columnsResult.size(); i++) {
      JsonObject columnRow = columnsResult.getJsonObject(i);
      String tableName = columnRow.getString("table_name");
      String columnName = columnRow.getString("column_name");
      String dataType = columnRow.getString("data_type");
      String ordinalPositionStr = columnRow.getString("ordinal_position");
      String isNullable = columnRow.getString("is_nullable");

      TableMetadata table = tableMap.get(tableName);
      if (table != null) {
        Integer ordinalPosition = ordinalPositionStr != null ? Integer.parseInt(ordinalPositionStr) : null;
        ColumnMetadata column = ColumnMetadata.builder()
            .columnName(columnName)
            .dataType(dataType)
            .ordinalPosition(ordinalPosition)
            .isNullable(isNullable)
            .build();
        table.getColumns().add(column);
      }
    }

    return tableMap.values().stream()
        .sorted((t1, t2) -> t1.getTableName().compareToIgnoreCase(t2.getTableName()))
        .collect(Collectors.toList());
  }
}


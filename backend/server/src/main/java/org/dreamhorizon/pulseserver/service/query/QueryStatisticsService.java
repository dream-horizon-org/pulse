package org.dreamhorizon.pulseserver.service.query;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.dreamhorizon.pulseserver.service.query.models.QueryStatistics;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class QueryStatisticsService {
  private final QueryJobDao queryJobDao;

  public Single<QueryStatistics> getQueryStatistics(String userEmail, LocalDateTime startDate, LocalDateTime endDate) {
    if (userEmail == null || userEmail.trim().isEmpty()) {
      return Single.error(new IllegalArgumentException("User email is required"));
    }

    final LocalDateTime finalStartDate = startDate != null ? startDate : LocalDateTime.now().minusDays(30);
    final LocalDateTime finalEndDate = endDate != null ? endDate : LocalDateTime.now();

    return queryJobDao.getQueriesForStatistics(userEmail, finalStartDate, finalEndDate)
        .map(queries -> calculateStatistics(queries, userEmail, finalStartDate, finalEndDate));
  }

  private QueryStatistics calculateStatistics(List<QueryJob> queries, String userEmail, 
      LocalDateTime startDate, LocalDateTime endDate) {
    QueryStatistics.QueryStatisticsSummary summary = QueryStatistics.QueryStatisticsSummary.builder()
        .totalQueries(queries.size())
        .succeeded((int) queries.stream().filter(q -> q.getStatus() == QueryJobStatus.COMPLETED).count())
        .failed((int) queries.stream().filter(q -> q.getStatus() == QueryJobStatus.FAILED).count())
        .cancelled((int) queries.stream().filter(q -> q.getStatus() == QueryJobStatus.CANCELLED).count())
        .running((int) queries.stream().filter(q -> q.getStatus() == QueryJobStatus.RUNNING).count())
        .build();

    List<QueryJob> completedQueries = queries.stream()
        .filter(q -> q.getStatus() == QueryJobStatus.COMPLETED)
        .collect(Collectors.toList());

    QueryStatistics.DataStatistics dataStats = calculateDataStatistics(completedQueries);
    QueryStatistics.TimeStatistics timeStats = calculateTimeStatistics(completedQueries);

    List<QueryStatistics.QueryStatisticItem> queryItems = queries.stream()
        .map(this::mapToQueryStatisticItem)
        .collect(Collectors.toList());

    return QueryStatistics.builder()
        .userEmail(userEmail)
        .period(QueryStatistics.Period.builder()
            .startDate(startDate)
            .endDate(endDate)
            .build())
        .summary(summary)
        .dataStatistics(dataStats)
        .timeStatistics(timeStats)
        .queries(queryItems)
        .build();
  }

  private QueryStatistics.DataStatistics calculateDataStatistics(List<QueryJob> queries) {
    if (queries.isEmpty()) {
      return QueryStatistics.DataStatistics.builder()
          .totalDataScannedBytes(0L)
          .totalDataScannedGB(0.0)
          .averageDataScannedBytes(0L)
          .maxDataScannedBytes(0L)
          .minDataScannedBytes(0L)
          .build();
    }

    List<Long> dataScannedList = queries.stream()
        .filter(q -> q.getDataScannedInBytes() != null)
        .map(QueryJob::getDataScannedInBytes)
        .collect(Collectors.toList());

    if (dataScannedList.isEmpty()) {
      return QueryStatistics.DataStatistics.builder()
          .totalDataScannedBytes(0L)
          .totalDataScannedGB(0.0)
          .averageDataScannedBytes(0L)
          .maxDataScannedBytes(0L)
          .minDataScannedBytes(0L)
          .build();
    }

    long total = dataScannedList.stream().mapToLong(Long::longValue).sum();
    long max = dataScannedList.stream().mapToLong(Long::longValue).max().orElse(0L);
    long min = dataScannedList.stream().mapToLong(Long::longValue).min().orElse(0L);
    long average = total / dataScannedList.size();
    double totalGB = total / (1024.0 * 1024.0 * 1024.0);

    return QueryStatistics.DataStatistics.builder()
        .totalDataScannedBytes(total)
        .totalDataScannedGB(totalGB)
        .averageDataScannedBytes(average)
        .maxDataScannedBytes(max)
        .minDataScannedBytes(min)
        .build();
  }

  private QueryStatistics.TimeStatistics calculateTimeStatistics(List<QueryJob> queries) {
    if (queries.isEmpty()) {
      return QueryStatistics.TimeStatistics.builder()
          .totalExecutionTimeMillis(0L)
          .totalExecutionTimeSeconds(0L)
          .averageExecutionTimeMillis(0L)
          .maxExecutionTimeMillis(0L)
          .minExecutionTimeMillis(0L)
          .build();
    }

    List<Long> executionTimes = queries.stream()
        .filter(q -> q.getExecutionTimeMillis() != null)
        .map(QueryJob::getExecutionTimeMillis)
        .collect(Collectors.toList());

    if (executionTimes.isEmpty()) {
      return QueryStatistics.TimeStatistics.builder()
          .totalExecutionTimeMillis(0L)
          .totalExecutionTimeSeconds(0L)
          .averageExecutionTimeMillis(0L)
          .maxExecutionTimeMillis(0L)
          .minExecutionTimeMillis(0L)
          .build();
    }

    long total = executionTimes.stream().mapToLong(Long::longValue).sum();
    long max = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0L);
    long min = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0L);
    long average = total / executionTimes.size();
    long totalSeconds = total / 1000;

    return QueryStatistics.TimeStatistics.builder()
        .totalExecutionTimeMillis(total)
        .totalExecutionTimeSeconds(totalSeconds)
        .averageExecutionTimeMillis(average)
        .maxExecutionTimeMillis(max)
        .minExecutionTimeMillis(min)
        .build();
  }

  private QueryStatistics.QueryStatisticItem mapToQueryStatisticItem(QueryJob job) {
    return QueryStatistics.QueryStatisticItem.builder()
        .jobId(job.getJobId())
        .queryExecutionId(job.getQueryExecutionId())
        .status(job.getStatus() != null ? job.getStatus().name() : null)
        .dataScannedInBytes(job.getDataScannedInBytes())
        .executionTimeMillis(job.getExecutionTimeMillis())
        .engineExecutionTimeMillis(job.getEngineExecutionTimeMillis())
        .queryQueueTimeMillis(job.getQueryQueueTimeMillis())
        .createdAt(job.getCreatedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }
}


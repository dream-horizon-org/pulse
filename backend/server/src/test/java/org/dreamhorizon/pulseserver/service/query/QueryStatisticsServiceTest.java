package org.dreamhorizon.pulseserver.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.dreamhorizon.pulseserver.service.query.models.QueryStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class QueryStatisticsServiceTest {

  @Mock
  QueryJobDao queryJobDao;

  QueryStatisticsService queryStatisticsService;

  @BeforeEach
  void setUp() {
    queryStatisticsService = new QueryStatisticsService(queryJobDao);
  }

  @Test
  void shouldRejectNullUserEmail() {
    var testObserver = queryStatisticsService.getQueryStatistics(null, null, null).test();

    testObserver.assertError(IllegalArgumentException.class);
    testObserver.assertError(error -> error.getMessage().contains("User email is required"));
  }

  @Test
  void shouldRejectEmptyUserEmail() {
    var testObserver = queryStatisticsService.getQueryStatistics("   ", null, null).test();

    testObserver.assertError(IllegalArgumentException.class);
  }

  @Test
  void shouldGetStatisticsWithEmptyQueries() {
    String userEmail = "test@example.com";
    LocalDateTime startDate = LocalDateTime.now().minusDays(7);
    LocalDateTime endDate = LocalDateTime.now();

    when(queryJobDao.getQueriesForStatistics(eq(userEmail), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(Single.just(Collections.emptyList()));

    QueryStatistics result = queryStatisticsService.getQueryStatistics(userEmail, startDate, endDate).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getUserEmail()).isEqualTo(userEmail);
    assertThat(result.getSummary().getTotalQueries()).isEqualTo(0);
    assertThat(result.getSummary().getSucceeded()).isEqualTo(0);
    assertThat(result.getSummary().getFailed()).isEqualTo(0);
    assertThat(result.getSummary().getCancelled()).isEqualTo(0);
    assertThat(result.getSummary().getRunning()).isEqualTo(0);
    assertThat(result.getDataStatistics().getTotalDataScannedBytes()).isEqualTo(0L);
    assertThat(result.getTimeStatistics().getTotalExecutionTimeMillis()).isEqualTo(0L);
    assertThat(result.getQueries()).isEmpty();
  }

  @Test
  void shouldGetStatisticsWithDefaultDates() {
    String userEmail = "test@example.com";

    when(queryJobDao.getQueriesForStatistics(eq(userEmail), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(Single.just(Collections.emptyList()));

    QueryStatistics result = queryStatisticsService.getQueryStatistics(userEmail, null, null).blockingGet();

    assertThat(result).isNotNull();
    verify(queryJobDao).getQueriesForStatistics(eq(userEmail), any(LocalDateTime.class), any(LocalDateTime.class));
  }

  @Test
  void shouldCalculateStatisticsWithMixedStatuses() {
    String userEmail = "test@example.com";
    LocalDateTime startDate = LocalDateTime.now().minusDays(7);
    LocalDateTime endDate = LocalDateTime.now();

    Timestamp now = new Timestamp(System.currentTimeMillis());
    List<QueryJob> queries = Arrays.asList(
        QueryJob.builder()
            .jobId("job-1")
            .status(QueryJobStatus.COMPLETED)
            .dataScannedInBytes(1000L)
            .executionTimeMillis(500L)
            .createdAt(now)
            .completedAt(now)
            .build(),
        QueryJob.builder()
            .jobId("job-2")
            .status(QueryJobStatus.FAILED)
            .createdAt(now)
            .build(),
        QueryJob.builder()
            .jobId("job-3")
            .status(QueryJobStatus.COMPLETED)
            .dataScannedInBytes(2000L)
            .executionTimeMillis(1000L)
            .createdAt(now)
            .completedAt(now)
            .build(),
        QueryJob.builder()
            .jobId("job-4")
            .status(QueryJobStatus.CANCELLED)
            .createdAt(now)
            .build(),
        QueryJob.builder()
            .jobId("job-5")
            .status(QueryJobStatus.RUNNING)
            .createdAt(now)
            .build()
    );

    when(queryJobDao.getQueriesForStatistics(eq(userEmail), eq(startDate), eq(endDate)))
        .thenReturn(Single.just(queries));

    QueryStatistics result = queryStatisticsService.getQueryStatistics(userEmail, startDate, endDate).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getSummary().getTotalQueries()).isEqualTo(5);
    assertThat(result.getSummary().getSucceeded()).isEqualTo(2);
    assertThat(result.getSummary().getFailed()).isEqualTo(1);
    assertThat(result.getSummary().getCancelled()).isEqualTo(1);
    assertThat(result.getSummary().getRunning()).isEqualTo(1);
    assertThat(result.getDataStatistics().getTotalDataScannedBytes()).isEqualTo(3000L);
    assertThat(result.getDataStatistics().getAverageDataScannedBytes()).isEqualTo(1500L);
    assertThat(result.getDataStatistics().getMaxDataScannedBytes()).isEqualTo(2000L);
    assertThat(result.getDataStatistics().getMinDataScannedBytes()).isEqualTo(1000L);
    assertThat(result.getTimeStatistics().getTotalExecutionTimeMillis()).isEqualTo(1500L);
    assertThat(result.getTimeStatistics().getAverageExecutionTimeMillis()).isEqualTo(750L);
    assertThat(result.getTimeStatistics().getMaxExecutionTimeMillis()).isEqualTo(1000L);
    assertThat(result.getTimeStatistics().getMinExecutionTimeMillis()).isEqualTo(500L);
    assertThat(result.getQueries()).hasSize(5);
  }

  @Test
  void shouldHandleQueriesWithNullDataScanned() {
    String userEmail = "test@example.com";
    LocalDateTime startDate = LocalDateTime.now().minusDays(7);
    LocalDateTime endDate = LocalDateTime.now();

    Timestamp now = new Timestamp(System.currentTimeMillis());
    List<QueryJob> queries = Arrays.asList(
        QueryJob.builder()
            .jobId("job-1")
            .status(QueryJobStatus.COMPLETED)
            .dataScannedInBytes(null)
            .executionTimeMillis(null)
            .createdAt(now)
            .completedAt(now)
            .build()
    );

    when(queryJobDao.getQueriesForStatistics(eq(userEmail), eq(startDate), eq(endDate)))
        .thenReturn(Single.just(queries));

    QueryStatistics result = queryStatisticsService.getQueryStatistics(userEmail, startDate, endDate).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getDataStatistics().getTotalDataScannedBytes()).isEqualTo(0L);
    assertThat(result.getTimeStatistics().getTotalExecutionTimeMillis()).isEqualTo(0L);
  }

  @Test
  void shouldHandleQueriesWithMixedNullAndNonNullData() {
    String userEmail = "test@example.com";
    LocalDateTime startDate = LocalDateTime.now().minusDays(7);
    LocalDateTime endDate = LocalDateTime.now();

    Timestamp now = new Timestamp(System.currentTimeMillis());
    List<QueryJob> queries = Arrays.asList(
        QueryJob.builder()
            .jobId("job-1")
            .status(QueryJobStatus.COMPLETED)
            .dataScannedInBytes(1000L)
            .executionTimeMillis(500L)
            .createdAt(now)
            .completedAt(now)
            .build(),
        QueryJob.builder()
            .jobId("job-2")
            .status(QueryJobStatus.COMPLETED)
            .dataScannedInBytes(null)
            .executionTimeMillis(null)
            .createdAt(now)
            .completedAt(now)
            .build(),
        QueryJob.builder()
            .jobId("job-3")
            .status(QueryJobStatus.COMPLETED)
            .dataScannedInBytes(2000L)
            .executionTimeMillis(1000L)
            .createdAt(now)
            .completedAt(now)
            .build()
    );

    when(queryJobDao.getQueriesForStatistics(eq(userEmail), eq(startDate), eq(endDate)))
        .thenReturn(Single.just(queries));

    QueryStatistics result = queryStatisticsService.getQueryStatistics(userEmail, startDate, endDate).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getDataStatistics().getTotalDataScannedBytes()).isEqualTo(3000L);
    assertThat(result.getDataStatistics().getAverageDataScannedBytes()).isEqualTo(1500L);
    assertThat(result.getTimeStatistics().getTotalExecutionTimeMillis()).isEqualTo(1500L);
    assertThat(result.getTimeStatistics().getAverageExecutionTimeMillis()).isEqualTo(750L);
  }

  @Test
  void shouldMapQueryStatisticItemsCorrectly() {
    String userEmail = "test@example.com";
    LocalDateTime startDate = LocalDateTime.now().minusDays(7);
    LocalDateTime endDate = LocalDateTime.now();

    Timestamp now = new Timestamp(System.currentTimeMillis());
    QueryJob job = QueryJob.builder()
        .jobId("job-1")
        .queryExecutionId("exec-1")
        .status(QueryJobStatus.COMPLETED)
        .dataScannedInBytes(1000L)
        .executionTimeMillis(500L)
        .engineExecutionTimeMillis(400L)
        .queryQueueTimeMillis(100L)
        .createdAt(now)
        .completedAt(now)
        .build();

    when(queryJobDao.getQueriesForStatistics(eq(userEmail), eq(startDate), eq(endDate)))
        .thenReturn(Single.just(Collections.singletonList(job)));

    QueryStatistics result = queryStatisticsService.getQueryStatistics(userEmail, startDate, endDate).blockingGet();

    assertThat(result.getQueries()).hasSize(1);
    QueryStatistics.QueryStatisticItem item = result.getQueries().get(0);
    assertThat(item.getJobId()).isEqualTo("job-1");
    assertThat(item.getQueryExecutionId()).isEqualTo("exec-1");
    assertThat(item.getStatus()).isEqualTo("COMPLETED");
    assertThat(item.getDataScannedInBytes()).isEqualTo(1000L);
    assertThat(item.getExecutionTimeMillis()).isEqualTo(500L);
    assertThat(item.getEngineExecutionTimeMillis()).isEqualTo(400L);
    assertThat(item.getQueryQueueTimeMillis()).isEqualTo(100L);
    assertThat(item.getCreatedAt()).isEqualTo(now);
    assertThat(item.getCompletedAt()).isEqualTo(now);
  }

  @Test
  void shouldHandleQueryWithNullStatus() {
    String userEmail = "test@example.com";
    LocalDateTime startDate = LocalDateTime.now().minusDays(7);
    LocalDateTime endDate = LocalDateTime.now();

    Timestamp now = new Timestamp(System.currentTimeMillis());
    QueryJob job = QueryJob.builder()
        .jobId("job-1")
        .status(null)
        .createdAt(now)
        .build();

    when(queryJobDao.getQueriesForStatistics(eq(userEmail), eq(startDate), eq(endDate)))
        .thenReturn(Single.just(Collections.singletonList(job)));

    QueryStatistics result = queryStatisticsService.getQueryStatistics(userEmail, startDate, endDate).blockingGet();

    assertThat(result.getQueries()).hasSize(1);
    assertThat(result.getQueries().get(0).getStatus()).isNull();
  }
}


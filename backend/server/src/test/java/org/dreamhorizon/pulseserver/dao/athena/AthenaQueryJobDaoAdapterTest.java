package org.dreamhorizon.pulseserver.dao.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.sql.Timestamp;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AthenaQueryJobDaoAdapterTest {

  @Mock
  AthenaJobDao athenaJobDao;

  AthenaQueryJobDaoAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AthenaQueryJobDaoAdapter(athenaJobDao);
  }

  @Nested
  class TestCreateJob {

    @Test
    void shouldCreateJob() {
      String queryString = "SELECT * FROM table";
      String jobId = "job-123";

      when(athenaJobDao.createJob(queryString)).thenReturn(Single.just(jobId));

      String result = adapter.createJob(queryString).blockingGet();

      assertThat(result).isEqualTo(jobId);
      verify(athenaJobDao).createJob(queryString);
    }
  }

  @Nested
  class TestUpdateJobWithExecutionId {

    @Test
    void shouldUpdateJobWithExecutionId() {
      String jobId = "job-123";
      String executionId = "exec-123";
      QueryJobStatus status = QueryJobStatus.RUNNING;

      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.RUNNING))
          .thenReturn(Single.just(true));

      Boolean result = adapter.updateJobWithExecutionId(jobId, executionId, status).blockingGet();

      assertThat(result).isTrue();
      verify(athenaJobDao).updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.RUNNING);
    }

    @Test
    void shouldMapAllStatusTypes() {
      String jobId = "job-123";
      String executionId = "exec-123";

      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.SUBMITTED))
          .thenReturn(Single.just(true));
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.RUNNING))
          .thenReturn(Single.just(true));
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.COMPLETED))
          .thenReturn(Single.just(true));
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.FAILED))
          .thenReturn(Single.just(true));
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.CANCELLED))
          .thenReturn(Single.just(true));

      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.SUBMITTED).blockingGet()).isTrue();
      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.RUNNING).blockingGet()).isTrue();
      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.COMPLETED).blockingGet()).isTrue();
      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.FAILED).blockingGet()).isTrue();
      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.CANCELLED).blockingGet()).isTrue();
    }
  }

  @Nested
  class TestUpdateJobStatus {

    @Test
    void shouldUpdateJobStatus() {
      String jobId = "job-123";
      QueryJobStatus status = QueryJobStatus.COMPLETED;

      when(athenaJobDao.updateJobStatus(jobId, AthenaJobStatus.COMPLETED))
          .thenReturn(Single.just(true));

      Boolean result = adapter.updateJobStatus(jobId, status).blockingGet();

      assertThat(result).isTrue();
      verify(athenaJobDao).updateJobStatus(jobId, AthenaJobStatus.COMPLETED);
    }
  }

  @Nested
  class TestUpdateJobCompleted {

    @Test
    void shouldUpdateJobCompleted() {
      String jobId = "job-123";
      String resultLocation = "s3://bucket/path";

      when(athenaJobDao.updateJobCompleted(jobId, resultLocation))
          .thenReturn(Single.just(true));

      Boolean result = adapter.updateJobCompleted(jobId, resultLocation).blockingGet();

      assertThat(result).isTrue();
      verify(athenaJobDao).updateJobCompleted(jobId, resultLocation);
    }
  }

  @Nested
  class TestUpdateJobFailed {

    @Test
    void shouldUpdateJobFailed() {
      String jobId = "job-123";
      String errorMessage = "Query failed";

      when(athenaJobDao.updateJobFailed(jobId, errorMessage))
          .thenReturn(Single.just(true));

      Boolean result = adapter.updateJobFailed(jobId, errorMessage).blockingGet();

      assertThat(result).isTrue();
      verify(athenaJobDao).updateJobFailed(jobId, errorMessage);
    }
  }

  @Nested
  class TestGetJobById {

    @Test
    void shouldMapAthenaJobToQueryJob() {
      String jobId = "job-123";
      Timestamp now = new Timestamp(System.currentTimeMillis());

      AthenaJob athenaJob = AthenaJob.builder()
          .jobId(jobId)
          .queryString("SELECT * FROM table")
          .queryExecutionId("exec-123")
          .status(AthenaJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .errorMessage(null)
          .resultData(null)
          .nextToken(null)
          .dataScannedInBytes(1000L)
          .createdAt(now)
          .updatedAt(now)
          .completedAt(now)
          .build();

      when(athenaJobDao.getJobById(jobId))
          .thenReturn(Single.just(athenaJob));

      QueryJob result = adapter.getJobById(jobId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getJobId()).isEqualTo(jobId);
      assertThat(result.getQueryString()).isEqualTo("SELECT * FROM table");
      assertThat(result.getQueryExecutionId()).isEqualTo("exec-123");
      assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
      assertThat(result.getResultLocation()).isEqualTo("s3://bucket/path");
      assertThat(result.getDataScannedInBytes()).isEqualTo(1000L);
    }

    @Test
    void shouldThrowNullPointerWhenJobNotFound() {
      String jobId = "job-123";

      when(athenaJobDao.getJobById(jobId))
          .thenReturn(Single.fromCallable(() -> (AthenaJob) null));

      assertThatThrownBy(() -> adapter.getJobById(jobId).blockingGet())
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldMapAllStatusTypes() {
      String jobId = "job-123";
      Timestamp now = new Timestamp(System.currentTimeMillis());

      when(athenaJobDao.getJobById(jobId))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .status(AthenaJobStatus.SUBMITTED)
              .createdAt(now)
              .updatedAt(now)
              .build()))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .status(AthenaJobStatus.RUNNING)
              .createdAt(now)
              .updatedAt(now)
              .build()))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .status(AthenaJobStatus.COMPLETED)
              .createdAt(now)
              .updatedAt(now)
              .build()))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .status(AthenaJobStatus.FAILED)
              .createdAt(now)
              .updatedAt(now)
              .build()))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .status(AthenaJobStatus.CANCELLED)
              .createdAt(now)
              .updatedAt(now)
              .build()));

      assertThat(adapter.getJobById(jobId).blockingGet().getStatus()).isEqualTo(QueryJobStatus.SUBMITTED);
      assertThat(adapter.getJobById(jobId).blockingGet().getStatus()).isEqualTo(QueryJobStatus.RUNNING);
      assertThat(adapter.getJobById(jobId).blockingGet().getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
      assertThat(adapter.getJobById(jobId).blockingGet().getStatus()).isEqualTo(QueryJobStatus.FAILED);
      assertThat(adapter.getJobById(jobId).blockingGet().getStatus()).isEqualTo(QueryJobStatus.CANCELLED);
    }
  }
}

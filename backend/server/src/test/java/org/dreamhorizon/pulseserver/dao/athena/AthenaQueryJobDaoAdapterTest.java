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
      String projectId = "test-project";
      String queryString = "SELECT * FROM table";
      String userEmail = "test@example.com";
      String jobId = "job-123";

      when(athenaJobDao.createJob(projectId, queryString, userEmail)).thenReturn(Single.just(jobId));

      String result = adapter.createJob(projectId, queryString, userEmail).blockingGet();

      assertThat(result).isEqualTo(jobId);
      verify(athenaJobDao).createJob(projectId, queryString, userEmail);
    }
  }

  @Nested
  class TestUpdateJobWithExecutionId {

    @Test
    void shouldUpdateJobWithExecutionId() {
      String jobId = "job-123";
      String executionId = "exec-123";
      QueryJobStatus status = QueryJobStatus.RUNNING;
      Timestamp submissionDateTime = new Timestamp(System.currentTimeMillis());

      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.RUNNING, submissionDateTime))
          .thenReturn(Single.just(true));

      Boolean result = adapter.updateJobWithExecutionId(jobId, executionId, status, submissionDateTime).blockingGet();

      assertThat(result).isTrue();
      verify(athenaJobDao).updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.RUNNING, submissionDateTime);
    }

    @Test
    void shouldMapAllStatusTypes() {
      String jobId = "job-123";
      String executionId = "exec-123";

      Timestamp submissionDateTime = new Timestamp(System.currentTimeMillis());
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.SUBMITTED, submissionDateTime))
          .thenReturn(Single.just(true));
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.RUNNING, submissionDateTime))
          .thenReturn(Single.just(true));
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.COMPLETED, submissionDateTime))
          .thenReturn(Single.just(true));
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.FAILED, submissionDateTime))
          .thenReturn(Single.just(true));
      when(athenaJobDao.updateJobWithExecutionId(jobId, executionId, AthenaJobStatus.CANCELLED, submissionDateTime))
          .thenReturn(Single.just(true));

      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.SUBMITTED, submissionDateTime).blockingGet()).isTrue();
      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.RUNNING, submissionDateTime).blockingGet()).isTrue();
      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.COMPLETED, submissionDateTime).blockingGet()).isTrue();
      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.FAILED, submissionDateTime).blockingGet()).isTrue();
      assertThat(adapter.updateJobWithExecutionId(jobId, executionId, QueryJobStatus.CANCELLED, submissionDateTime).blockingGet()).isTrue();
    }
  }

  @Nested
  class TestUpdateJobStatus {

    @Test
    void shouldUpdateJobStatus() {
      String jobId = "job-123";
      QueryJobStatus status = QueryJobStatus.COMPLETED;
      Timestamp updatedAt = new Timestamp(System.currentTimeMillis());

      when(athenaJobDao.updateJobStatus(jobId, AthenaJobStatus.COMPLETED, updatedAt))
          .thenReturn(Single.just(true));

      Boolean result = adapter.updateJobStatus(jobId, status, updatedAt).blockingGet();

      assertThat(result).isTrue();
      verify(athenaJobDao).updateJobStatus(jobId, AthenaJobStatus.COMPLETED, updatedAt);
    }
  }

  @Nested
  class TestUpdateJobCompleted {

    @Test
    void shouldUpdateJobCompleted() {
      String jobId = "job-123";
      String resultLocation = "s3://bucket/path";
      Timestamp completionDateTime = new Timestamp(System.currentTimeMillis());

      when(athenaJobDao.updateJobCompleted(jobId, resultLocation, completionDateTime))
          .thenReturn(Single.just(true));

      Boolean result = adapter.updateJobCompleted(jobId, resultLocation, completionDateTime).blockingGet();

      assertThat(result).isTrue();
      verify(athenaJobDao).updateJobCompleted(jobId, resultLocation, completionDateTime);
    }
  }

  @Nested
  class TestUpdateJobFailed {

    @Test
    void shouldUpdateJobFailed() {
      String jobId = "job-123";
      String errorMessage = "Query failed";
      Timestamp completionDateTime = new Timestamp(System.currentTimeMillis());

      when(athenaJobDao.updateJobFailed(jobId, errorMessage, completionDateTime))
          .thenReturn(Single.just(true));

      Boolean result = adapter.updateJobFailed(jobId, errorMessage, completionDateTime).blockingGet();

      assertThat(result).isTrue();
      verify(athenaJobDao).updateJobFailed(jobId, errorMessage, completionDateTime);
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
          .userEmail("test@example.com")
          .queryExecutionId("exec-123")
          .status(AthenaJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .errorMessage(null)
          .resultData(null)
          .nextToken(null)
          .dataScannedInBytes(1000L)
          .executionTimeMillis(null)
          .engineExecutionTimeMillis(null)
          .queryQueueTimeMillis(null)
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
              .queryString("SELECT * FROM table")
              .userEmail("test@example.com")
              .status(AthenaJobStatus.SUBMITTED)
              .createdAt(now)
              .updatedAt(now)
              .build()))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .queryString("SELECT * FROM table")
              .userEmail("test@example.com")
              .status(AthenaJobStatus.RUNNING)
              .createdAt(now)
              .updatedAt(now)
              .build()))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .queryString("SELECT * FROM table")
              .userEmail("test@example.com")
              .status(AthenaJobStatus.COMPLETED)
              .createdAt(now)
              .updatedAt(now)
              .build()))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .queryString("SELECT * FROM table")
              .userEmail("test@example.com")
              .status(AthenaJobStatus.FAILED)
              .createdAt(now)
              .updatedAt(now)
              .build()))
          .thenReturn(Single.just(AthenaJob.builder()
              .jobId(jobId)
              .queryString("SELECT * FROM table")
              .userEmail("test@example.com")
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

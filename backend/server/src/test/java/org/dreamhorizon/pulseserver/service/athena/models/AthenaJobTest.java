package org.dreamhorizon.pulseserver.service.athena.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.vertx.core.json.JsonArray;
import java.sql.Timestamp;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AthenaJobTest {

  @Nested
  class TestAthenaJob {

    @Test
    void shouldCreateWithNoArgs() {
      AthenaJob job = new AthenaJob();
      assertNotNull(job);
    }

    @Test
    void shouldCreateWithAllArgs() {
      String jobId = "job-123";
      String queryString = "SELECT * FROM table";
      String queryExecutionId = "exec-123";
      AthenaJobStatus status = AthenaJobStatus.RUNNING;
      String resultLocation = "s3://bucket/path";
      String errorMessage = null;
      JsonArray resultData = new JsonArray();
      String nextToken = "token-123";
      Long dataScannedInBytes = 1000L;
      Timestamp createdAt = new Timestamp(System.currentTimeMillis());
      Timestamp updatedAt = new Timestamp(System.currentTimeMillis());
      Timestamp completedAt = new Timestamp(System.currentTimeMillis());

      AthenaJob job = new AthenaJob(jobId, queryString, queryExecutionId, status, resultLocation,
          errorMessage, resultData, nextToken, dataScannedInBytes, createdAt, updatedAt, completedAt);

      assertEquals(jobId, job.getJobId());
      assertEquals(queryString, job.getQueryString());
      assertEquals(queryExecutionId, job.getQueryExecutionId());
      assertEquals(status, job.getStatus());
      assertEquals(resultLocation, job.getResultLocation());
      assertEquals(errorMessage, job.getErrorMessage());
      assertEquals(resultData, job.getResultData());
      assertEquals(nextToken, job.getNextToken());
      assertEquals(dataScannedInBytes, job.getDataScannedInBytes());
      assertEquals(createdAt, job.getCreatedAt());
      assertEquals(updatedAt, job.getUpdatedAt());
      assertEquals(completedAt, job.getCompletedAt());
    }

    @Test
    void shouldCreateWithBuilder() {
      String jobId = "job-456";
      AthenaJobStatus status = AthenaJobStatus.COMPLETED;

      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(status)
          .build();

      assertEquals(jobId, job.getJobId());
      assertEquals(status, job.getStatus());
    }

    @Test
    void shouldSetAndGetFields() {
      AthenaJob job = new AthenaJob();
      job.setJobId("job-789");
      job.setStatus(AthenaJobStatus.FAILED);
      job.setDataScannedInBytes(5000L);

      assertEquals("job-789", job.getJobId());
      assertEquals(AthenaJobStatus.FAILED, job.getStatus());
      assertEquals(5000L, job.getDataScannedInBytes());
    }

    @Test
    void shouldHandleNullValues() {
      AthenaJob job = AthenaJob.builder().build();

      assertNull(job.getJobId());
      assertNull(job.getStatus());
      assertNull(job.getResultData());
      assertNull(job.getNextToken());
      assertNull(job.getDataScannedInBytes());
    }
  }
}


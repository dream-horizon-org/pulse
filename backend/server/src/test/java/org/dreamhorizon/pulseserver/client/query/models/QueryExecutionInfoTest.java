package org.dreamhorizon.pulseserver.client.query.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

public class QueryExecutionInfoTest {

  @Test
  void shouldCreateWithBuilder() {
    String executionId = "exec-123";
    QueryStatus status = QueryStatus.SUCCEEDED;
    String stateChangeReason = "Query succeeded";
    String resultLocation = "s3://bucket/path";
    Long dataScannedBytes = 1000L;
    Long executionTimeMillis = 500L;
    Long engineExecutionTimeMillis = 400L;
    Long queryQueueTimeMillis = 100L;
    Timestamp submissionTime = new Timestamp(System.currentTimeMillis());
    Timestamp completionTime = new Timestamp(System.currentTimeMillis() + 5000);

    QueryExecutionInfo info = QueryExecutionInfo.builder()
        .queryExecutionId(executionId)
        .status(status)
        .stateChangeReason(stateChangeReason)
        .resultLocation(resultLocation)
        .dataScannedInBytes(dataScannedBytes)
        .executionTimeMillis(executionTimeMillis)
        .engineExecutionTimeMillis(engineExecutionTimeMillis)
        .queryQueueTimeMillis(queryQueueTimeMillis)
        .submissionDateTime(submissionTime)
        .completionDateTime(completionTime)
        .build();

    assertThat(info).isNotNull();
    assertThat(info.getQueryExecutionId()).isEqualTo(executionId);
    assertThat(info.getStatus()).isEqualTo(status);
    assertThat(info.getStateChangeReason()).isEqualTo(stateChangeReason);
    assertThat(info.getResultLocation()).isEqualTo(resultLocation);
    assertThat(info.getDataScannedInBytes()).isEqualTo(dataScannedBytes);
    assertThat(info.getExecutionTimeMillis()).isEqualTo(executionTimeMillis);
    assertThat(info.getEngineExecutionTimeMillis()).isEqualTo(engineExecutionTimeMillis);
    assertThat(info.getQueryQueueTimeMillis()).isEqualTo(queryQueueTimeMillis);
    assertThat(info.getSubmissionDateTime()).isEqualTo(submissionTime);
    assertThat(info.getCompletionDateTime()).isEqualTo(completionTime);
  }

  @Test
  void shouldCreateWithNullValues() {
    QueryExecutionInfo info = QueryExecutionInfo.builder()
        .queryExecutionId(null)
        .status(null)
        .stateChangeReason(null)
        .resultLocation(null)
        .dataScannedInBytes(null)
        .executionTimeMillis(null)
        .engineExecutionTimeMillis(null)
        .queryQueueTimeMillis(null)
        .submissionDateTime(null)
        .completionDateTime(null)
        .build();

    assertThat(info).isNotNull();
    assertThat(info.getQueryExecutionId()).isNull();
    assertThat(info.getStatus()).isNull();
    assertThat(info.getStateChangeReason()).isNull();
    assertThat(info.getResultLocation()).isNull();
    assertThat(info.getDataScannedInBytes()).isNull();
    assertThat(info.getExecutionTimeMillis()).isNull();
    assertThat(info.getEngineExecutionTimeMillis()).isNull();
    assertThat(info.getQueryQueueTimeMillis()).isNull();
    assertThat(info.getSubmissionDateTime()).isNull();
    assertThat(info.getCompletionDateTime()).isNull();
  }

  @Test
  void shouldSetAndGetValues() {
    QueryExecutionInfo info = QueryExecutionInfo.builder().build();
    
    String executionId = "exec-456";
    QueryStatus status = QueryStatus.RUNNING;
    Timestamp now = new Timestamp(System.currentTimeMillis());

    info.setQueryExecutionId(executionId);
    info.setStatus(status);
    info.setSubmissionDateTime(now);

    assertThat(info.getQueryExecutionId()).isEqualTo(executionId);
    assertThat(info.getStatus()).isEqualTo(status);
    assertThat(info.getSubmissionDateTime()).isEqualTo(now);
  }
}


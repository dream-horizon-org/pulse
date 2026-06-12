package org.dreamhorizon.pulseserver.dao.athena;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AthenaJobQueriesTest {

  @Test
  void shouldHaveCreateJobQuery() {
    String query = AthenaJobQueries.CREATE_JOB;
    assertNotNull(query);
    assertTrue(query.contains("INSERT INTO athena_job"));
    assertTrue(query.contains("job_id"));
    assertTrue(query.contains("query_string"));
    assertTrue(query.contains("status"));
  }

  @Test
  void shouldHaveUpdateJobWithExecutionIdQuery() {
    String query = AthenaJobQueries.UPDATE_JOB_WITH_EXECUTION_ID;
    assertNotNull(query);
    assertTrue(query.contains("UPDATE athena_job"));
    assertTrue(query.contains("query_execution_id"));
  }

  @Test
  void shouldHaveUpdateJobStatusQuery() {
    String query = AthenaJobQueries.UPDATE_JOB_STATUS;
    assertNotNull(query);
    assertTrue(query.contains("UPDATE athena_job"));
    assertTrue(query.contains("status"));
  }

  @Test
  void shouldHaveUpdateJobCompletedQuery() {
    String query = AthenaJobQueries.UPDATE_JOB_COMPLETED;
    assertNotNull(query);
    assertTrue(query.contains("UPDATE athena_job"));
    assertTrue(query.contains("COMPLETED"));
    assertTrue(query.contains("result_location"));
  }

  @Test
  void shouldHaveUpdateJobFailedQuery() {
    String query = AthenaJobQueries.UPDATE_JOB_FAILED;
    assertNotNull(query);
    assertTrue(query.contains("UPDATE athena_job"));
    assertTrue(query.contains("FAILED"));
    assertTrue(query.contains("error_message"));
  }

  @Test
  void shouldHaveGetJobByIdQuery() {
    String query = AthenaJobQueries.GET_JOB_BY_ID;
    assertNotNull(query);
    assertTrue(query.contains("SELECT"));
    assertTrue(query.contains("FROM athena_job"));
    assertTrue(query.contains("job_id"));
  }
}


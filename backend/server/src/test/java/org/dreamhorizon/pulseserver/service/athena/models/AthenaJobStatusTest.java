package org.dreamhorizon.pulseserver.service.athena.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AthenaJobStatusTest {

  @Nested
  class TestAthenaJobStatus {

    @Test
    void shouldHaveAllExpectedValues() {
      AthenaJobStatus[] statuses = AthenaJobStatus.values();
      assertNotNull(statuses);
      assertTrue(statuses.length > 0);
    }

    @Test
    void shouldHaveSubmittedStatus() {
      AthenaJobStatus status = AthenaJobStatus.SUBMITTED;
      assertNotNull(status);
      assertEquals("SUBMITTED", status.name());
    }

    @Test
    void shouldHaveRunningStatus() {
      AthenaJobStatus status = AthenaJobStatus.RUNNING;
      assertNotNull(status);
      assertEquals("RUNNING", status.name());
    }

    @Test
    void shouldHaveCompletedStatus() {
      AthenaJobStatus status = AthenaJobStatus.COMPLETED;
      assertNotNull(status);
      assertEquals("COMPLETED", status.name());
    }

    @Test
    void shouldHaveFailedStatus() {
      AthenaJobStatus status = AthenaJobStatus.FAILED;
      assertNotNull(status);
      assertEquals("FAILED", status.name());
    }

    @Test
    void shouldHaveCancelledStatus() {
      AthenaJobStatus status = AthenaJobStatus.CANCELLED;
      assertNotNull(status);
      assertEquals("CANCELLED", status.name());
    }

    @Test
    void shouldGetValueByName() {
      assertEquals(AthenaJobStatus.SUBMITTED, AthenaJobStatus.valueOf("SUBMITTED"));
      assertEquals(AthenaJobStatus.RUNNING, AthenaJobStatus.valueOf("RUNNING"));
      assertEquals(AthenaJobStatus.COMPLETED, AthenaJobStatus.valueOf("COMPLETED"));
      assertEquals(AthenaJobStatus.FAILED, AthenaJobStatus.valueOf("FAILED"));
      assertEquals(AthenaJobStatus.CANCELLED, AthenaJobStatus.valueOf("CANCELLED"));
    }
  }
}


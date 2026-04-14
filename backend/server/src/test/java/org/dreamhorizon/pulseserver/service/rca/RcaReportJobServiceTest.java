package org.dreamhorizon.pulseserver.service.rca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import io.vertx.mysqlclient.MySQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RcaReportJobServiceTest {

  private static final LocalDate DATE = LocalDate.of(2025, 6, 1);

  @Mock
  private RcaReportJobDao jobDao;

  @Mock
  private RcaReportCacheDao cacheDao;

  private RcaReportJobService service;

  @BeforeEach
  void setUp() {
    service = new RcaReportJobService(jobDao, cacheDao, new ObjectMapper());
  }

  private RcaReportJob activeJob(String id) {
    return new RcaReportJob(
        id,
        "p1",
        "ix",
        DATE,
        RcaJobStatus.PROCESSING,
        null,
        Instant.parse("2025-06-01T10:00:00Z"),
        Instant.parse("2025-06-01T10:00:01Z"),
        null,
        null,
        null,
        2);
  }

  @Test
  void shouldRecognizeMysqlDuplicateKeyMessage() {
    assertThat(RcaReportJobService.isDuplicateKey(new Exception("Duplicate entry for key")))
        .isTrue();
    assertThat(RcaReportJobService.isDuplicateKey(new Exception("errorCode=1062: dup"))).isTrue();
    assertThat(RcaReportJobService.isDuplicateKey(new MySQLException("Duplicate", 1062, "23000")))
        .isTrue();
    assertThat(
            RcaReportJobService.isDuplicateKey(
                new RuntimeException(new MySQLException("dup", 1062, "23000"))))
        .isTrue();
    assertThat(RcaReportJobService.isDuplicateKey(new Exception("timeout"))).isFalse();
  }

  @Nested
  class CreateOrGetJob {

    @Test
    void shouldReturnExistingWithoutInsertWhenActiveJobPresent() {
      RcaReportJob existing = activeJob("j1");
      when(jobDao.getActiveJobByKey("p1", "ix", DATE)).thenReturn(Maybe.just(existing));

      RcaJobDispatch dispatch =
          service
              .createOrGetJob(new RcaCacheKey("p1", "ix", DATE, false, "{}"), "u1")
              .blockingGet();

      assertThat(dispatch.shouldEnqueueWorker()).isFalse();
      assertThat(dispatch.job().jobId()).isEqualTo("j1");
    }

    @Test
    void shouldInsertAndEnqueueWhenNoActiveJob() {
      RcaReportJob created = activeJob("j-new");
      when(jobDao.getActiveJobByKey("p1", "ix", DATE)).thenReturn(Maybe.empty());
      when(jobDao.createJob(anyString(), eq("p1"), eq("ix"), eq(DATE), eq("u1")))
          .thenReturn(Single.just(created));

      RcaJobDispatch dispatch =
          service
              .createOrGetJob(new RcaCacheKey("p1", "ix", DATE, false, "{}"), "u1")
              .blockingGet();

      assertThat(dispatch.shouldEnqueueWorker()).isTrue();
      assertThat(dispatch.job().jobId()).isEqualTo("j-new");
    }

    @Test
    void shouldRecoverActiveJobOnDuplicateInsert() {
      RcaReportJob winner = activeJob("j-winner");
      when(jobDao.getActiveJobByKey("p1", "ix", DATE))
          .thenReturn(Maybe.empty())
          .thenReturn(Maybe.just(winner));
      when(jobDao.createJob(anyString(), eq("p1"), eq("ix"), eq(DATE), any()))
          .thenReturn(
              Single.error(
                  new RuntimeException("errorCode=1062: Duplicate entry for key 'uk_active_job'")));

      RcaJobDispatch dispatch =
          service
              .createOrGetJob(new RcaCacheKey("p1", "ix", DATE, false, "{\"a\":1}"), "u1")
              .blockingGet();

      assertThat(dispatch.shouldEnqueueWorker()).isFalse();
      assertThat(dispatch.job().jobId()).isEqualTo("j-winner");
    }
  }
}

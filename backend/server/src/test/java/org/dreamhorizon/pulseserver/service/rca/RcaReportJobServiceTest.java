package org.dreamhorizon.pulseserver.service.rca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.mysqlclient.MySQLException;
import java.time.Instant;
import java.time.LocalDate;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.dao.rcareport.models.RcaReportCacheHit;
import org.dreamhorizon.pulseserver.resources.v1.ai.models.GetRcaJobResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RcaReportJobServiceTest {

  private static final LocalDate DATE = LocalDate.of(2025, 6, 1);
  private static final RcaType TYPE = RcaType.INTERACTION;
  private static final String ENTITY_KEY = "ix";

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
        TYPE,
        ENTITY_KEY,
        DATE,
        RcaJobStatus.PROCESSING,
        null,
        Instant.parse("2025-06-01T10:00:00Z"),
        Instant.parse("2025-06-01T10:00:01Z"),
        null,
        null,
        null);
  }

  @Test
  void shouldRecognizeMysqlDuplicateKeyException() {
    assertThat(RcaReportJobService.isDuplicateKey(new MySQLException("Duplicate", 1062, "23000")))
        .isTrue();
    assertThat(
            RcaReportJobService.isDuplicateKey(
                new RuntimeException(new MySQLException("dup", 1062, "23000"))))
        .isTrue();
    assertThat(RcaReportJobService.isDuplicateKey(new Exception("Duplicate entry for key")))
        .isFalse();
    assertThat(RcaReportJobService.isDuplicateKey(new Exception("timeout"))).isFalse();
  }

  @Nested
  class CreateOrGetJob {

    @Test
    void shouldReturnExistingWithoutInsertWhenActiveJobPresent() {
      RcaReportJob existing = activeJob("j1");
      when(jobDao.getActiveJobByKey("p1", TYPE, ENTITY_KEY, DATE)).thenReturn(Maybe.just(existing));

      RcaJobDispatch dispatch =
          service
              .createOrGetJob(new RcaCacheKey("p1", TYPE, ENTITY_KEY, DATE, false, "{}"), "u1")
              .blockingGet();

      assertThat(dispatch.shouldEnqueueWorker()).isFalse();
      assertThat(dispatch.job().jobId()).isEqualTo("j1");
    }

    @Test
    void shouldInsertAndEnqueueWhenNoActiveJob() {
      RcaReportJob created = activeJob("j-new");
      when(jobDao.getActiveJobByKey("p1", TYPE, ENTITY_KEY, DATE)).thenReturn(Maybe.empty());
      when(jobDao.createJob(anyString(), eq("p1"), eq(TYPE), eq(ENTITY_KEY), eq(DATE), eq("u1")))
          .thenReturn(Single.just(created));

      RcaJobDispatch dispatch =
          service
              .createOrGetJob(new RcaCacheKey("p1", TYPE, ENTITY_KEY, DATE, false, "{}"), "u1")
              .blockingGet();

      assertThat(dispatch.shouldEnqueueWorker()).isTrue();
      assertThat(dispatch.job().jobId()).isEqualTo("j-new");
    }

    @Test
    void shouldRecoverActiveJobOnDuplicateInsert() {
      RcaReportJob winner = activeJob("j-winner");
      when(jobDao.getActiveJobByKey("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.empty())
          .thenReturn(Maybe.just(winner));
      when(jobDao.createJob(anyString(), eq("p1"), eq(TYPE), eq(ENTITY_KEY), eq(DATE), any()))
          .thenReturn(
              Single.error(new io.vertx.mysqlclient.MySQLException(
                  "Duplicate entry for key 'uk_active_job'", 1062, "23000")));

      RcaJobDispatch dispatch =
          service
              .createOrGetJob(new RcaCacheKey("p1", TYPE, ENTITY_KEY, DATE, false, "{\"a\":1}"), "u1")
              .blockingGet();

      assertThat(dispatch.shouldEnqueueWorker()).isFalse();
      assertThat(dispatch.job().jobId()).isEqualTo("j-winner");
    }
  }

  @Nested
  class PeekStatus {

    @Test
    void shouldReturnCompletedResponseOnCacheHit() {
      String reportJson = "{\"structured\":null}";
      Instant cachedAt = Instant.parse("2025-06-01T10:00:00Z");
      when(cacheDao.get("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.just(new RcaReportCacheHit(reportJson, cachedAt)));

      GetRcaJobResponse response = service.peekStatus("p1", TYPE, ENTITY_KEY, DATE).blockingGet();

      assertThat(response).isNotNull();
      assertThat(response.getStatus()).isEqualTo(RcaJobStatus.COMPLETED.name());
      assertThat(response.getReport()).isNotNull();
      assertThat(response.getCached()).isTrue();
      assertThat(response.getCachedAt()).isEqualTo(cachedAt);
      assertThat(response.getJobId()).isNull();
    }

    @Test
    void shouldExtractInnerReportFieldFromCacheBody() {
      // Cache body has the full shape: { "report": { "structured": {...} }, "cached": true }
      String cacheBody = "{\"report\":{\"structured\":null},\"cached\":true,\"cachedAt\":\"2025-06-01T10:00:00Z\"}";
      Instant cachedAt = Instant.parse("2025-06-01T10:00:00Z");
      when(cacheDao.get("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.just(new RcaReportCacheHit(cacheBody, cachedAt)));

      GetRcaJobResponse response = service.peekStatus("p1", TYPE, ENTITY_KEY, DATE).blockingGet();

      assertThat(response.getReport()).isNotNull();
      // report field must be the inner object { "structured": null }, not the full cache body
      assertThat(response.getReport().has("structured")).isTrue();
      assertThat(response.getReport().has("cached")).isFalse();
    }

    @Test
    void shouldMergeTopLevelRootCausePayloadIntoExtractedReport() {
      String cacheBody =
          "{"
              + "\"report\":{\"structured\":null},"
              + "\"rootCausePayload\":{\"baseline\":{},\"segments\":[]},"
              + "\"cached\":true,"
              + "\"cachedAt\":\"2025-06-01T10:00:00Z\""
              + "}";
      Instant cachedAt = Instant.parse("2025-06-01T10:00:00Z");
      when(cacheDao.get("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.just(new RcaReportCacheHit(cacheBody, cachedAt)));

      GetRcaJobResponse response = service.peekStatus("p1", TYPE, ENTITY_KEY, DATE).blockingGet();

      assertThat(response.getReport()).isNotNull();
      assertThat(response.getReport().has("structured")).isTrue();
      assertThat(response.getReport().has("rootCausePayload")).isTrue();
      assertThat(response.getReport().path("rootCausePayload").path("segments").isArray()).isTrue();
      assertThat(response.getReport().has("cached")).isFalse();
    }

    @Test
    void shouldReturnInnerReportUnmergedWhenInnerIsNotObjectNode() {
      String cacheBody =
          "{"
              + "\"report\":\"scalar-inner\","
              + "\"rootCausePayload\":{\"baseline\":{},\"segments\":[]}"
              + "}";
      Instant cachedAt = Instant.parse("2025-06-01T10:00:00Z");
      when(cacheDao.get("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.just(new RcaReportCacheHit(cacheBody, cachedAt)));

      GetRcaJobResponse response = service.peekStatus("p1", TYPE, ENTITY_KEY, DATE).blockingGet();

      assertThat(response.getReport()).isNotNull();
      assertThat(response.getReport().isTextual()).isTrue();
      assertThat(response.getReport().asText()).isEqualTo("scalar-inner");
    }

    @Test
    void shouldNotOverwriteInnerRootCausePayloadWithTopLevelSibling() {
      String cacheBody =
          "{"
              + "\"report\":{"
              + "\"structured\":null,"
              + "\"rootCausePayload\":{\"baseline\":{\"x\":1},\"segments\":[]}"
              + "},"
              + "\"rootCausePayload\":{\"baseline\":{\"y\":2},\"segments\":[]}"
              + "}";
      when(cacheDao.get("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(
              Maybe.just(new RcaReportCacheHit(cacheBody, Instant.parse("2025-06-01T10:00:00Z"))));

      GetRcaJobResponse response = service.peekStatus("p1", TYPE, ENTITY_KEY, DATE).blockingGet();

      assertThat(response.getReport().path("rootCausePayload").path("baseline").path("x").asInt())
          .isEqualTo(1);
      assertThat(response.getReport().path("rootCausePayload").path("baseline").has("y")).isFalse();
    }

    @Test
    void shouldReturnActiveJobWhenCacheEmpty() {
      when(cacheDao.get("p1", TYPE, ENTITY_KEY, DATE)).thenReturn(Maybe.empty());
      when(jobDao.getActiveJobByKey("p1", TYPE, ENTITY_KEY, DATE)).thenReturn(Maybe.just(activeJob("j1")));

      GetRcaJobResponse response = service.peekStatus("p1", TYPE, ENTITY_KEY, DATE).blockingGet();

      assertThat(response).isNotNull();
      assertThat(response.getJobId()).isEqualTo("j1");
      assertThat(response.getStatus()).isEqualTo(RcaJobStatus.PROCESSING.name());
    }

    @Test
    void shouldReturnEmptyWhenNeitherCacheNorActiveJob() {
      when(cacheDao.get("p1", TYPE, ENTITY_KEY, DATE)).thenReturn(Maybe.empty());
      when(jobDao.getActiveJobByKey("p1", TYPE, ENTITY_KEY, DATE)).thenReturn(Maybe.empty());

      GetRcaJobResponse response = service.peekStatus("p1", TYPE, ENTITY_KEY, DATE).blockingGet();

      assertThat(response).isNull();
    }

    @Test
    void shouldReturnCompletedWithNullReportOnMalformedCachedJson() {
      when(cacheDao.get("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.just(new RcaReportCacheHit("{{not-valid-json", Instant.now())));

      GetRcaJobResponse response = service.peekStatus("p1", TYPE, ENTITY_KEY, DATE).blockingGet();

      assertThat(response).isNotNull();
      assertThat(response.getStatus()).isEqualTo(RcaJobStatus.COMPLETED.name());
      assertThat(response.getReport()).isNull();
      assertThat(response.getCached()).isNull();
    }
  }

  @Nested
  class GetJobStatus {

    @Test
    void shouldReturnNotFoundWhenJobMissing() {
      when(jobDao.getJobById("unknown")).thenReturn(Maybe.empty());

      assertThatThrownBy(() -> service.getJobStatus("unknown", "p1").blockingGet())
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReturnNotFoundOnProjectIdMismatch() {
      when(jobDao.getJobById("j1")).thenReturn(Maybe.just(activeJob("j1")));

      assertThatThrownBy(() -> service.getJobStatus("j1", "wrong-project").blockingGet())
          .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReturnJobWithReportWhenCompleted() {
      RcaReportJob completedJob =
          new RcaReportJob(
              "j1", "p1", TYPE, ENTITY_KEY, DATE, RcaJobStatus.COMPLETED,
              null,
              Instant.parse("2025-06-01T10:00:00Z"),
              Instant.parse("2025-06-01T10:00:01Z"),
              Instant.parse("2025-06-01T10:05:00Z"),
              null, null);
      Instant cachedAt = Instant.parse("2025-06-01T10:05:00Z");
      when(jobDao.getJobById("j1")).thenReturn(Maybe.just(completedJob));
      when(cacheDao.getFromWriterPool("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.just(new RcaReportCacheHit("{\"structured\":null}", cachedAt)));

      GetRcaJobResponse response = service.getJobStatus("j1", "p1").blockingGet();

      assertThat(response.getJobId()).isEqualTo("j1");
      assertThat(response.getStatus()).isEqualTo("COMPLETED");
      assertThat(response.getReport()).isNotNull();
      assertThat(response.getCached()).isTrue();
      assertThat(response.getCachedAt()).isEqualTo(cachedAt);
    }

    @Test
    void shouldExtractInnerReportFieldWhenCompletedCacheBodyHasReportWrapper() {
      RcaReportJob completedJob =
          new RcaReportJob(
              "j1", "p1", TYPE, ENTITY_KEY, DATE, RcaJobStatus.COMPLETED,
              null,
              Instant.parse("2025-06-01T10:00:00Z"),
              Instant.parse("2025-06-01T10:00:01Z"),
              Instant.parse("2025-06-01T10:05:00Z"),
              null, null);
      // Full cache body shape: { "report": { "structured": {...} }, "cached": true, ... }
      String cacheBody = "{\"report\":{\"structured\":null},\"cached\":true,\"cachedAt\":\"2025-06-01T10:05:00Z\"}";
      when(jobDao.getJobById("j1")).thenReturn(Maybe.just(completedJob));
      when(cacheDao.getFromWriterPool("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.just(new RcaReportCacheHit(cacheBody, Instant.parse("2025-06-01T10:05:00Z"))));

      GetRcaJobResponse response = service.getJobStatus("j1", "p1").blockingGet();

      assertThat(response.getReport()).isNotNull();
      // report must be the inner object, not the full cache body with cached/cachedAt
      assertThat(response.getReport().has("structured")).isTrue();
      assertThat(response.getReport().has("cached")).isFalse();
    }

    @Test
    void shouldMergeTopLevelRootCausePayloadWhenCompletedJobLoadsCache() {
      RcaReportJob completedJob =
          new RcaReportJob(
              "j1", "p1", TYPE, ENTITY_KEY, DATE, RcaJobStatus.COMPLETED,
              null,
              Instant.parse("2025-06-01T10:00:00Z"),
              Instant.parse("2025-06-01T10:00:01Z"),
              Instant.parse("2025-06-01T10:05:00Z"),
              null, null);
      String cacheBody =
          "{"
              + "\"report\":{\"structured\":null},"
              + "\"rootCausePayload\":{\"baseline\":{},\"segments\":[]},"
              + "\"cached\":true"
              + "}";
      when(jobDao.getJobById("j1")).thenReturn(Maybe.just(completedJob));
      when(cacheDao.getFromWriterPool("p1", TYPE, ENTITY_KEY, DATE))
          .thenReturn(Maybe.just(new RcaReportCacheHit(cacheBody, Instant.parse("2025-06-01T10:05:00Z"))));

      GetRcaJobResponse response = service.getJobStatus("j1", "p1").blockingGet();

      assertThat(response.getReport()).isNotNull();
      assertThat(response.getReport().has("rootCausePayload")).isTrue();
    }

    @Test
    void shouldReturnJobWithoutReportWhenProcessing() {
      when(jobDao.getJobById("j1")).thenReturn(Maybe.just(activeJob("j1")));

      GetRcaJobResponse response = service.getJobStatus("j1", "p1").blockingGet();

      assertThat(response.getJobId()).isEqualTo("j1");
      assertThat(response.getStatus()).isEqualTo("PROCESSING");
      assertThat(response.getReport()).isNull();
      assertThat(response.getPollUrl()).contains("/v1/ai-rca/job/j1");
    }
  }
}

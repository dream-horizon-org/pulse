package org.dreamhorizon.pulseserver.service.rca;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaReportJobDao;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.service.ai.impl.AiUpstreamProxyExecutor;
import org.dreamhorizon.pulseserver.service.errorattribution.RcaReportErrorAttributionMerger;
import org.dreamhorizon.pulseserver.service.rootcause.RcaRelatedHeatmapsMerger;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RcaReportProcessorTest {

  private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
  private static final String JOB_ID = "rca-job-x";
  private static final String BODY = "{\"interactionName\":\"ix\"}";

  @Mock private Vertx vertx;
  @Mock private RcaReportJobDao jobDao;
  @Mock private RcaReportCacheDao cacheDao;
  @Mock private RootCauseService rootCauseService;
  @Mock private RcaReportEnrichmentService enrichmentService;
  @Mock private RcaReportErrorAttributionMerger rcaReportErrorAttributionMerger;
  @Mock private WebClient webClient;
  @Mock private HttpRequest<Buffer> httpRequest;

  private RcaReportProcessor processor;

  @BeforeEach
  void setUp() {
    AiUpstreamProxyExecutor upstream = new AiUpstreamProxyExecutor(webClient, "http://ai-test");
    processor =
        new RcaReportProcessor(
            vertx,
            jobDao,
            cacheDao,
            new ObjectMapper(),
            rootCauseService,
            RootCauseConfig.withDefaults(null),
            new RcaRelatedHeatmapsMerger(new ObjectMapper()),
            rcaReportErrorAttributionMerger,
            enrichmentService,
            upstream);

    doNothing()
        .when(rcaReportErrorAttributionMerger)
        .mergeInto(any(), anyString(), anyString(), any(), any(), anyInt());
    when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
    when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
    when(jobDao.updateStatus(any(), any())).thenReturn(Completable.complete());
    when(jobDao.markCompleted(any(), any(), any(), any(), any())).thenReturn(Completable.complete());
    when(jobDao.markFailed(any(), any(), any(), any(), any(), any())).thenReturn(Completable.complete());
    when(cacheDao.put(any(), any(), any(), any(), any())).thenReturn(Completable.complete());
  }

  private RcaReportJob job() {
    return new RcaReportJob(
        JOB_ID, "p1", RcaType.INTERACTION, "ix", DATE, RcaJobStatus.PENDING, null,
        Instant.now(), null, null, null, null);
  }

  /** Stubs vertx.executeBlocking to run the callable synchronously on the calling thread. */
  @SuppressWarnings("unchecked")
  private void stubSyncExecution() {
    doAnswer(
            inv -> {
              Callable<Object> callable = inv.getArgument(0);
              Handler<AsyncResult<Object>> handler = inv.getArgument(2);
              try {
                handler.handle(io.vertx.core.Future.succeededFuture(callable.call()));
              } catch (Exception e) {
                handler.handle(io.vertx.core.Future.failedFuture(e));
              }
              return null;
            })
        .when(vertx)
        .executeBlocking(
            ArgumentMatchers.<Callable<Object>>any(),
            anyBoolean(),
            ArgumentMatchers.<Handler<AsyncResult<Object>>>any());
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<Buffer> mockHttpResponse(int status, String body) {
    HttpResponse<Buffer> resp = mock(HttpResponse.class);
    when(resp.statusCode()).thenReturn(status);
    when(resp.getHeader("Content-Type")).thenReturn("application/json");
    when(resp.body()).thenReturn(Buffer.buffer(body.getBytes(StandardCharsets.UTF_8)));
    return resp;
  }

  private RcaEnrichmentOutcome simpleOutcome() {
    return new RcaEnrichmentOutcome(BODY, null, DATE, Instant.now(), false);
  }

  @Test
  void shouldDelegateToWorkerPoolOnEnqueue() {
    processor.enqueueProcess(job(), BODY, false, "Bearer t", null);

    verify(vertx)
        .executeBlocking(
            ArgumentMatchers.<Callable<Object>>any(),
            eq(false),
            ArgumentMatchers.<Handler<AsyncResult<Object>>>any());
  }

  @Test
  void shouldMarkCompletedAfterSuccessfulPipeline() {
    stubSyncExecution();
    when(enrichmentService.enrichAsync(any(), anyBoolean()))
        .thenReturn(CompletableFuture.completedFuture(simpleOutcome()));
    HttpResponse<Buffer> resp200 = mockHttpResponse(200, "{\"report\":\"ok\"}");
    when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(resp200));

    processor.enqueueProcess(job(), BODY, false, "Bearer t", null);

    verify(jobDao).updateStatus(JOB_ID, RcaJobStatus.PROCESSING);
    verify(cacheDao).put(eq("p1"), eq(RcaType.INTERACTION), eq("ix"), eq(DATE), any());
    verify(jobDao).markCompleted(JOB_ID, "p1", RcaType.INTERACTION, "ix", DATE);
  }

  @Test
  void shouldMarkFailedWhenAiUpstreamReturnsNonSuccessStatus() {
    stubSyncExecution();
    when(enrichmentService.enrichAsync(any(), anyBoolean()))
        .thenReturn(CompletableFuture.completedFuture(simpleOutcome()));
    HttpResponse<Buffer> resp500 = mockHttpResponse(500, "{\"error\":\"model failed\"}");
    when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(resp500));

    processor.enqueueProcess(job(), BODY, false, "Bearer t", null);

    verify(jobDao).markFailed(eq(JOB_ID), eq("p1"), eq(RcaType.INTERACTION), eq("ix"), eq(DATE), eq("model failed"));
    verify(jobDao, never()).markCompleted(any(), any(), any(), any(), any());
  }

  @Test
  void shouldExtractMessageFieldFromUpstreamError() {
    stubSyncExecution();
    when(enrichmentService.enrichAsync(any(), anyBoolean()))
        .thenReturn(CompletableFuture.completedFuture(simpleOutcome()));
    HttpResponse<Buffer> resp400 = mockHttpResponse(400, "{\"message\":\"bad request\"}");
    when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(resp400));

    processor.enqueueProcess(job(), BODY, false, "Bearer t", null);

    verify(jobDao).markFailed(eq(JOB_ID), eq("p1"), eq(RcaType.INTERACTION), eq("ix"), eq(DATE), eq("bad request"));
  }

  @Test
  void shouldFallbackToGenericMessageWhenUpstreamBodyIsEmpty() {
    stubSyncExecution();
    when(enrichmentService.enrichAsync(any(), anyBoolean()))
        .thenReturn(CompletableFuture.completedFuture(simpleOutcome()));
    HttpResponse<Buffer> resp503 = mockHttpResponse(503, "");
    when(httpRequest.rxSendBuffer(any())).thenReturn(Single.just(resp503));

    processor.enqueueProcess(job(), BODY, false, "Bearer t", null);

    verify(jobDao).markFailed(
        eq(JOB_ID), eq("p1"), eq(RcaType.INTERACTION), eq("ix"), eq(DATE),
        argThat(msg -> msg.contains("HTTP 503")));
  }

  @Test
  void shouldMarkFailedWhenPipelineThrowsException() {
    stubSyncExecution();
    when(jobDao.updateStatus(any(), any()))
        .thenReturn(Completable.error(new RuntimeException("db down")));

    processor.enqueueProcess(job(), BODY, false, "Bearer t", null);

    verify(jobDao).markFailed(eq(JOB_ID), eq("p1"), eq(RcaType.INTERACTION), eq("ix"), eq(DATE), any());
    verify(jobDao, never()).markCompleted(any(), any(), any(), any(), any());
  }

  @Test
  void shouldMarkFailedOnMalformedRequestBody() {
    stubSyncExecution();

    processor.enqueueProcess(job(), "{not-valid-json}", false, "Bearer t", null);

    verify(jobDao).markFailed(eq(JOB_ID), eq("p1"), eq(RcaType.INTERACTION), eq("ix"), eq(DATE), any());
  }

  @Test
  void shouldTruncateErrorMessageWhenItExceedsLimit() {
    stubSyncExecution();
    String longError = "x".repeat(5000);
    when(jobDao.updateStatus(any(), any()))
        .thenReturn(Completable.error(new RuntimeException(longError)));

    processor.enqueueProcess(job(), BODY, false, "Bearer t", null);

    verify(jobDao).markFailed(
        eq(JOB_ID), eq("p1"), eq(RcaType.INTERACTION), eq("ix"), eq(DATE),
        argThat(msg -> msg != null && msg.length() <= 4000));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldMarkFailedFromEnqueueErrorHandlerWhenWorkerFails() {
    doAnswer(
            inv -> {
              Handler<AsyncResult<Object>> handler = inv.getArgument(2);
              handler.handle(io.vertx.core.Future.failedFuture(new RuntimeException("worker boom")));
              return null;
            })
        .when(vertx)
        .executeBlocking(
            ArgumentMatchers.<Callable<Object>>any(),
            anyBoolean(),
            ArgumentMatchers.<Handler<AsyncResult<Object>>>any());

    processor.enqueueProcess(job(), BODY, false, "Bearer t", null);

    verify(jobDao).markFailed(eq(JOB_ID), eq("p1"), eq(RcaType.INTERACTION), eq("ix"), eq(DATE), any());
  }
}

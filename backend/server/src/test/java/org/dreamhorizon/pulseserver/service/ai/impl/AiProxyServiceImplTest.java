package org.dreamhorizon.pulseserver.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.dao.rcareport.models.RcaReportCacheHit;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rca.RcaCacheKey;
import org.dreamhorizon.pulseserver.service.rca.RcaJobDispatch;
import org.dreamhorizon.pulseserver.service.rca.RcaReportJobService;
import org.dreamhorizon.pulseserver.service.rca.RcaReportProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiProxyServiceImplTest {

  private static final String AI_SERVICE_URL = "http://ai-service:8000";
  private static final String AUTH = "Bearer token";
  private static final String PROJECT_ID = "project-1";
  private static final LocalDate ANALYSIS_DATE = LocalDate.of(2025, 3, 10);

  @Mock
  private WebClient webClient;

  @Mock
  private HttpRequest<Buffer> httpRequest;

  @Mock
  private RcaReportCacheDao rcaReportCacheDao;

  @Mock
  private RcaReportJobService rcaReportJobService;

  @Mock
  private RcaReportProcessor rcaReportProcessor;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    lenient()
        .when(rcaReportCacheDao.put(any(), any(), any(), any(), any()))
        .thenReturn(Completable.complete());
    lenient().when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.putAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.deleteAbs(anyString())).thenReturn(httpRequest);
    lenient().when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
    lenient().when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
    lenient()
        .when(rcaReportJobService.createOrGetJob(any(), any()))
        .thenAnswer(
            inv -> {
              RcaCacheKey key = inv.getArgument(0);
              RcaReportJob job = samplePendingJob();
              // key may be null when Mockito captures invocations during re-stubbing
              String body = key != null ? key.requestBody() : "{}";
              boolean regenerate = key != null && key.regenerate();
              return Single.just(new RcaJobDispatch(job, true, body, regenerate));
            });
  }

  private RcaReportJob samplePendingJob() {
    return new RcaReportJob(
        "rca-job-unit",
        PROJECT_ID,
        RcaType.INTERACTION,
        "checkout",
        ANALYSIS_DATE,
        RcaJobStatus.PENDING,
        null,
        Instant.parse("2025-03-10T10:00:00Z"),
        null,
        null,
        null,
        null);
  }

  private AiProxyServiceImpl fullPipelineService() {
    return new AiProxyServiceImpl(
        webClient, AI_SERVICE_URL, objectMapper, rcaReportCacheDao, rcaReportJobService,
        rcaReportProcessor);
  }

  private HttpResponse<Buffer> mockBufferedResponse(int status, String contentType, String body) {
    HttpResponse<Buffer> response = org.mockito.Mockito.mock(HttpResponse.class);
    when(response.getHeader("Content-Type")).thenReturn(contentType);
    when(response.statusCode()).thenReturn(status);
    String payload = body == null ? "" : body;
    when(response.body()).thenReturn(Buffer.buffer(payload.getBytes(StandardCharsets.UTF_8)));
    return response;
  }

  private void stubSendReturns(HttpResponse<Buffer> upstreamResponse) {
    when(httpRequest.rxSend()).thenReturn(Single.just(upstreamResponse));
    when(httpRequest.rxSendBuffer(any(Buffer.class))).thenReturn(Single.just(upstreamResponse));
  }

  private void stubSendErrors(Throwable error) {
    when(httpRequest.rxSend()).thenReturn(Single.error(error));
    when(httpRequest.rxSendBuffer(any(Buffer.class))).thenReturn(Single.error(error));
  }

  private AiProxyUpstreamResult awaitResult(CompletionStage<AiProxyUpstreamResult> stage) {
    try {
      return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private String rcaRequestBody() {
    return "{\"rcaType\":\"INTERACTION\",\"entityKey\":\"checkout\",\"date\":\"2025-03-10\"}";
  }

  @Nested
  class RcaPipelineDisabled {

    @Test
    void shouldTreatRcaReportAsPlainProxyWhenDepsNotInjected() {
      AiProxyServiceImpl service = new AiProxyServiceImpl(webClient, AI_SERVICE_URL);
      String body = rcaRequestBody();
      HttpResponse<Buffer> upstreamResponse =
          mockBufferedResponse(200, "application/json", "{\"ok\":true}");
      stubSendReturns(upstreamResponse);

      AiProxyUpstreamResult result =
          awaitResult(service.proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      assertThat(result.getBufferedBody()).contains("ok");
      verify(rcaReportJobService, never()).createOrGetJob(any(), any());
      verify(rcaReportCacheDao, never()).get(any(), any(), any(), any());

      ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
      verify(webClient).postAbs(urlCaptor.capture());
      assertThat(urlCaptor.getValue()).isEqualTo(AI_SERVICE_URL + "/rca/report");
      verify(httpRequest).timeout(AiProxyServiceImpl.AI_PROXY_UPSTREAM_TIMEOUT_MS);
    }
  }

  @Nested
  class RcaPipelineEnabled {

    @Test
    void shouldReturnMysqlHitWithoutCallingUpstream() throws Exception {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq(RcaType.INTERACTION), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(
              Maybe.just(
                  new RcaReportCacheHit("{\"fromDb\":1}", Instant.parse("2025-03-10T08:30:00Z"))));

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      JsonNode node = objectMapper.readTree(result.getBufferedBody());
      assertThat(node.path("fromDb").asInt()).isEqualTo(1);
      assertThat(node.path("cached").asBoolean()).isTrue();
      assertThat(node.path("cachedAt").asText()).isEqualTo("2025-03-10T08:30:00Z");

      verify(httpRequest, never()).rxSend();
      verify(httpRequest, never()).rxSendBuffer(any(Buffer.class));
      verify(rcaReportJobService, never()).createOrGetJob(any(), any());
    }

    @Test
    void shouldReturnMysqlHitUnchangedWhenBodyIsNotJsonObject() {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq(RcaType.INTERACTION), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Maybe.just(new RcaReportCacheHit("[1,2]", null)));

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      assertThat(result.getBufferedBody()).isEqualTo("[1,2]");
      verify(httpRequest, never()).rxSend();
      verify(httpRequest, never()).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldRejectRcaReportWhenInteractionNameMissing() throws Exception {
      String body = "{\"date\":\"2025-03-10\"}";

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(400);
      JsonNode envelope = objectMapper.readTree(result.getBufferedBody());
      assertThat(envelope.path("error").path("code").asText()).isEqualTo("BE1002");
      assertThat(envelope.path("error").path("message").asText())
          .isEqualTo("entityKey is required");

      verify(rcaReportCacheDao, never()).get(any(), any(), any(), any());
      verify(rcaReportJobService, never()).createOrGetJob(any(), any());
      verify(httpRequest, never()).rxSend();
      verify(httpRequest, never()).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldRejectRcaReportWhenBodyMissing() throws Exception {
      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, "", AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(400);
      JsonNode envelope = objectMapper.readTree(result.getBufferedBody());
      assertThat(envelope.path("error").path("code").asText()).isEqualTo("BE1002");
      assertThat(envelope.path("error").path("message").asText())
          .isEqualTo("Request body is required");

      verify(httpRequest, never()).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldRejectRcaReportWhenProjectIdMissing() throws Exception {
      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, null));

      assertThat(result.getStatusCode()).isEqualTo(400);
      JsonNode envelope = objectMapper.readTree(result.getBufferedBody());
      assertThat(envelope.path("error").path("code").asText()).isEqualTo("BE1005");
      assertThat(envelope.path("error").path("message").asText())
          .isEqualTo("X-Project-ID header is required");

      verify(httpRequest, never()).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldFailWithDatabaseErrorWhenMysqlGetErrors() throws Exception {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq(RcaType.INTERACTION), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Maybe.error(new RuntimeException("connection refused")));

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(500);
      JsonNode envelope = objectMapper.readTree(result.getBufferedBody());
      assertThat(envelope.path("error").path("code").asText()).isEqualTo("500");
      assertThat(envelope.path("error").path("message").asText()).isEqualTo("Database Error");

      verify(httpRequest, never()).rxSendBuffer(any(Buffer.class));
      verify(rcaReportJobService, never()).createOrGetJob(any(), any());
    }

    @Test
    void shouldReturn202AndEnqueueWorkerWhenMysqlMisses() throws Exception {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq(RcaType.INTERACTION), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Maybe.empty());

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(202);
      JsonNode out = objectMapper.readTree(result.getBufferedBody());
      assertThat(out.path("jobId").asText()).isEqualTo("rca-job-unit");
      assertThat(out.path("pollUrl").asText()).contains("/v1/ai-rca/job/rca-job-unit");

      verify(rcaReportJobService, times(1)).createOrGetJob(any(), any());
      verify(rcaReportProcessor, times(1))
          .enqueueProcess(any(), anyString(), anyBoolean(), eq(AUTH), eq(null));
      verify(httpRequest, never()).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldReturn202WhenMysqlMissesEvenForStructuredUpstreamScenario() throws Exception {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq(RcaType.INTERACTION), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Maybe.empty());

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(202);
      verify(rcaReportProcessor, times(1))
          .enqueueProcess(any(), anyString(), anyBoolean(), eq(AUTH), eq(null));
    }

    @Test
    void shouldReturn500WhenJobCreationFailsAfterMysqlMiss() throws Exception {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq(RcaType.INTERACTION), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Maybe.empty());
      when(rcaReportJobService.createOrGetJob(any(), any()))
          .thenReturn(Single.error(new RuntimeException("job insert failed")));

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(500);
      JsonNode envelope = objectMapper.readTree(result.getBufferedBody());
      assertThat(envelope.path("error").path("code").asText()).isEqualTo("BE1007");
      verify(rcaReportProcessor, never())
          .enqueueProcess(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void shouldReturn202WithoutMysqlPutFromProxyThread() throws Exception {
      when(rcaReportCacheDao.get(any(), any(), any(), any())).thenReturn(Maybe.empty());

      awaitResult(
          fullPipelineService()
              .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      verify(rcaReportCacheDao, never()).put(any(), any(), any(), any(), any());
      verify(rcaReportProcessor, times(1))
          .enqueueProcess(any(), anyString(), anyBoolean(), eq(AUTH), eq(null));
    }

    @Test
    void shouldReturn202WhenMysqlMissesWithoutCallingUpstreamInProxyThread() {
      when(rcaReportCacheDao.get(any(), any(), any(), any())).thenReturn(Maybe.empty());

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(202);
      verify(httpRequest, never()).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldRequeryMysqlAndEnqueueOnEachRequestWhenDaoMisses() {
      when(rcaReportCacheDao.get(any(), any(), any(), any())).thenReturn(Maybe.empty());

      AiProxyServiceImpl service = fullPipelineService();
      String body = rcaRequestBody();

      awaitResult(service.proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));
      awaitResult(service.proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));

      verify(rcaReportJobService, times(2)).createOrGetJob(any(), any());
      verify(rcaReportProcessor, times(2))
          .enqueueProcess(any(), anyString(), anyBoolean(), eq(AUTH), eq(null));
      verify(rcaReportCacheDao, times(2)).get(PROJECT_ID, RcaType.INTERACTION, "checkout", ANALYSIS_DATE);
    }

    @Test
    void shouldSkipMysqlAndPassRegenerateOnJobWhenRegenerateTrue() throws Exception {
      String body = "{\"rcaType\":\"INTERACTION\",\"entityKey\":\"checkout\",\"date\":\"2025-03-10\",\"regenerate\":true}";

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService().proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));

      verify(rcaReportCacheDao, never()).get(any(), any(), any(), any());
      ArgumentCaptor<RcaCacheKey> keyCaptor = ArgumentCaptor.forClass(RcaCacheKey.class);
      verify(rcaReportJobService).createOrGetJob(keyCaptor.capture(), any());
      assertThat(keyCaptor.getValue().regenerate()).isTrue();
      assertThat(keyCaptor.getValue().requestBody()).isEqualTo(body);

      assertThat(result.getStatusCode()).isEqualTo(202);
      verify(rcaReportProcessor, times(1))
          .enqueueProcess(any(), anyString(), anyBoolean(), eq(AUTH), eq(null));
    }

    @Test
    void shouldPassUserEmailHeaderToCreateOrGetJob() throws Exception {
      when(rcaReportCacheDao.get(any(), any(), any(), any())).thenReturn(Maybe.empty());

      awaitResult(
          fullPipelineService()
              .proxy(
                  "POST",
                  "rca/report",
                  null,
                  rcaRequestBody(),
                  AUTH,
                  PROJECT_ID,
                  "reporter@example.com"));

      verify(rcaReportJobService).createOrGetJob(any(), eq("reporter@example.com"));
    }
  }

  @Nested
  class NonRcaProxy {

    @Test
    void shouldUseStreamingBodyWhenContentTypeIsSse() {
      AiProxyServiceImpl service = fullPipelineService();
      HttpResponse<Buffer> upstreamResponse =
          mockBufferedResponse(200, "text/event-stream; charset=utf-8", "data: ping\n\n");
      stubSendReturns(upstreamResponse);

      AiProxyUpstreamResult result =
          awaitResult(service.proxy("GET", "events", null, null, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      assertThat(result.isBuffered()).isFalse();
      assertThat(result.getMediaType()).contains("text/event-stream");
      assertThat(result.getStreamBody()).isNotNull();
    }

    @Test
    void shouldMapSendAsyncFailureToBadGateway() {
      AiProxyServiceImpl service = new AiProxyServiceImpl(webClient, AI_SERVICE_URL);
      stubSendErrors(new RuntimeException("network"));

      AiProxyUpstreamResult result =
          awaitResult(service.proxy("GET", "health", null, null, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(ServiceError.AI_PROXY_BAD_GATEWAY.getHttpStatusCode());
      assertThat(result.getBufferedBody()).contains(ServiceError.AI_PROXY_BAD_GATEWAY.getErrorMessage());
      verifyNoInteractions(rcaReportJobService);
    }
  }
}

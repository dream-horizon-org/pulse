package org.dreamhorizon.pulseserver.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.dao.rcareport.models.RcaReportCacheHit;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
  private RootCauseService rootCauseService;

  @Mock
  private RcaReportCacheDao rcaReportCacheDao;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    lenient()
        .when(rcaReportCacheDao.put(any(), any(), any(), any()))
        .thenReturn(Completable.complete());
    lenient().when(webClient.getAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.postAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.putAbs(anyString())).thenReturn(httpRequest);
    lenient().when(webClient.deleteAbs(anyString())).thenReturn(httpRequest);
    lenient().when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
    lenient().when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
  }

  private AiProxyServiceImpl fullPipelineService() {
    return new AiProxyServiceImpl(
        webClient, AI_SERVICE_URL, objectMapper, rootCauseService, rcaReportCacheDao);
  }

  private HttpResponse<Buffer> mockBufferedResponse(int status, String contentType, String body) {
    HttpResponse<Buffer> response =
        org.mockito.Mockito.mock(HttpResponse.class);
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
    return "{\"interactionName\":\"checkout\",\"date\":\"2025-03-10\"}";
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
      verify(rootCauseService, never()).getRootCause(any(), any(), any());
      verify(rcaReportCacheDao, never()).get(any(), any(), any());

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
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE)))
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
      verify(rootCauseService, never()).getRootCause(any(), any(), any());
    }

    @Test
    void shouldReturnMysqlHitUnchangedWhenBodyIsNotJsonObject() {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE)))
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
    void shouldSkipMysqlAndEnrichmentWhenInteractionNameMissing() {
      String body = "{\"date\":\"2025-03-10\"}";
      HttpResponse<Buffer> upstreamResponse =
          mockBufferedResponse(200, "application/json", "{}");
      stubSendReturns(upstreamResponse);

      awaitResult(
          fullPipelineService()
              .proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));

      verify(rcaReportCacheDao, never()).get(any(), any(), any());
      verify(rootCauseService, never()).getRootCause(any(), any(), any());
    }

    @Test
    void shouldFailWithDatabaseErrorWhenMysqlGetErrors() throws Exception {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE)))
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
      verify(rootCauseService, never()).getRootCause(any(), any(), any());
    }

    @Test
    void shouldCallGetRootCauseAndUpstreamWhenMysqlMisses() {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Maybe.empty());
      RootCauseResult rc =
          RootCauseResult.builder()
              .everythingGood(true)
              .baseline(Map.of())
              .segments(List.of())
              .build();
      when(rootCauseService.getRootCause(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Single.just(rc));
      HttpResponse<Buffer> upstreamResponse =
          mockBufferedResponse(200, "application/json", "{\"report\":\"ai\"}");
      stubSendReturns(upstreamResponse);

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      assertThat(result.getBufferedBody()).contains("report");

      verify(rootCauseService, times(1)).getRootCause(PROJECT_ID, "checkout", ANALYSIS_DATE);
      verify(httpRequest, times(1)).rxSendBuffer(any(Buffer.class));
      verify(rcaReportCacheDao, timeout(3000))
          .put(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE), eq("{\"report\":\"ai\"}"));
    }

    @Test
    void shouldReturnStandardErrorEnvelopeWhenCachePutFailsAfterSuccessfulUpstream()
        throws Exception {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Maybe.empty());
      when(rootCauseService.getRootCause(any(), any(), any()))
          .thenReturn(
              Single.just(
                  RootCauseResult.builder().segments(List.of()).baseline(Map.of()).build()));
      HttpResponse<Buffer> upstreamResponse =
          mockBufferedResponse(200, "application/json", "{\"report\":\"ai\"}");
      stubSendReturns(upstreamResponse);
      when(rcaReportCacheDao.put(any(), any(), any(), any()))
          .thenThrow(new RuntimeException("put failed"));

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(500);
      JsonNode envelope = objectMapper.readTree(result.getBufferedBody());
      assertThat(envelope.path("error").path("code").asText()).isEqualTo("BE1007");
      assertThat(envelope.path("error").path("message").asText())
          .isEqualTo("Internal error generating RCA report");
    }

    @Test
    void shouldSkipMysqlPutWhenUpstreamReturnsError() {
      when(rcaReportCacheDao.get(any(), any(), any())).thenReturn(Maybe.empty());
      when(rootCauseService.getRootCause(any(), any(), any()))
          .thenReturn(
              Single.just(
                  RootCauseResult.builder().segments(List.of()).baseline(Map.of()).build()));
      HttpResponse<Buffer> errorResponse =
          mockBufferedResponse(500, "application/json", "{\"error\":\"x\"}");
      stubSendReturns(errorResponse);

      awaitResult(
          fullPipelineService()
              .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      verify(rcaReportCacheDao, never()).put(any(), any(), any(), any());
    }

    @Test
    void shouldStillProxyWhenRootCauseFetchFails() {
      when(rcaReportCacheDao.get(any(), any(), any())).thenReturn(Maybe.empty());
      when(rootCauseService.getRootCause(any(), any(), any()))
          .thenReturn(Single.error(new RuntimeException("clickhouse down")));
      HttpResponse<Buffer> upstreamResponse = mockBufferedResponse(200, "application/json", "{}");
      stubSendReturns(upstreamResponse);

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      verify(rootCauseService, times(1)).getRootCause(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE));
      verify(httpRequest, times(1)).rxSendBuffer(any(Buffer.class));
    }

    @Test
    void shouldRequeryMysqlAndUpstreamOnEachRequestWhenDaoMisses() {
      when(rcaReportCacheDao.get(any(), any(), any())).thenReturn(Maybe.empty());
      when(rootCauseService.getRootCause(any(), any(), any()))
          .thenReturn(
              Single.just(
                  RootCauseResult.builder().segments(List.of()).baseline(Map.of()).build()));
      HttpResponse<Buffer> upstreamResponse =
          mockBufferedResponse(200, "application/json", "{\"once\":true}");
      stubSendReturns(upstreamResponse);

      AiProxyServiceImpl service = fullPipelineService();
      String body = rcaRequestBody();

      awaitResult(service.proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));
      awaitResult(service.proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));

      verify(rootCauseService, times(2)).getRootCause(any(), any(), any());
      verify(httpRequest, times(2)).rxSendBuffer(any(Buffer.class));
      verify(rcaReportCacheDao, times(2)).get(PROJECT_ID, "checkout", ANALYSIS_DATE);
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

      assertThat(result.getStatusCode()).isEqualTo(502);
      assertThat(result.getBufferedBody()).contains("unavailable");
      verifyNoInteractions(rootCauseService);
    }
  }

}

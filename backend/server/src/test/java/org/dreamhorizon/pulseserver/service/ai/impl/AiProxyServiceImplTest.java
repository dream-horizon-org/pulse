package org.dreamhorizon.pulseserver.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
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
@SuppressWarnings("unchecked")
class AiProxyServiceImplTest {

  private static final String AI_SERVICE_URL = "http://ai-service:8000";
  private static final String AUTH = "Bearer token";
  private static final String PROJECT_ID = "project-1";
  private static final LocalDate ANALYSIS_DATE = LocalDate.of(2025, 3, 10);

  @Mock
  private HttpClient httpClient;

  @Mock
  private RootCauseService rootCauseService;

  @Mock
  private RcaReportCacheDao rcaReportCacheDao;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  private AiProxyServiceImpl fullPipelineService() {
    return new AiProxyServiceImpl(
        httpClient, AI_SERVICE_URL, "", objectMapper, rootCauseService, rcaReportCacheDao);
  }

  private AiProxyServiceImpl fullPipelineServiceWithServiceKey(String key) {
    return new AiProxyServiceImpl(
        httpClient, AI_SERVICE_URL, key, objectMapper, rootCauseService, rcaReportCacheDao);
  }

  private HttpResponse<InputStream> mockBufferedResponse(int status, String contentType, String body) {
    HttpResponse<InputStream> response =
        (HttpResponse<InputStream>) org.mockito.Mockito.mock(HttpResponse.class);
    HttpHeaders headers = org.mockito.Mockito.mock(HttpHeaders.class);
    when(headers.firstValue("Content-Type")).thenReturn(Optional.of(contentType));
    when(response.headers()).thenReturn(headers);
    when(response.statusCode()).thenReturn(status);
    when(response.body())
        .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    return response;
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
      AiProxyServiceImpl service = new AiProxyServiceImpl(httpClient, AI_SERVICE_URL);
      String body = rcaRequestBody();
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  mockBufferedResponse(200, "application/json", "{\"ok\":true}")));

      AiProxyUpstreamResult result =
          awaitResult(service.proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      assertThat(result.getBufferedBody()).contains("ok");
      verify(rootCauseService, never()).getRootCause(any(), any(), any());
      verify(rcaReportCacheDao, never()).get(any(), any(), any());

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      assertThat(captor.getValue().uri().toString()).isEqualTo(AI_SERVICE_URL + "/rca/report");
    }
  }

  @Nested
  class RcaPipelineEnabled {

    @Test
    void shouldReturnMysqlHitWithoutCallingUpstream() throws Exception {
      when(rcaReportCacheDao.get(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE)))
          .thenReturn(Maybe.just("{\"fromDb\":1}"));

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      JsonNode node = objectMapper.readTree(result.getBufferedBody());
      assertThat(node.path("fromDb").asInt()).isEqualTo(1);
      assertThat(node.path("cached").asBoolean()).isTrue();

      verify(httpClient, never()).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
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
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  mockBufferedResponse(200, "application/json", "{\"report\":\"ai\"}")));

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      assertThat(result.getBufferedBody()).contains("report");

      verify(rootCauseService, times(1)).getRootCause(PROJECT_ID, "checkout", ANALYSIS_DATE);
      verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
      verify(rcaReportCacheDao, timeout(3000))
          .put(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE), eq("{\"report\":\"ai\"}"));
    }

    @Test
    void shouldSkipMysqlPutWhenUpstreamReturnsError() {
      when(rcaReportCacheDao.get(any(), any(), any())).thenReturn(Maybe.empty());
      when(rootCauseService.getRootCause(any(), any(), any()))
          .thenReturn(
              Single.just(
                  RootCauseResult.builder().segments(List.of()).baseline(Map.of()).build()));
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  mockBufferedResponse(500, "application/json", "{\"error\":\"x\"}")));

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
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  mockBufferedResponse(200, "application/json", "{}")));

      AiProxyUpstreamResult result =
          awaitResult(
              fullPipelineService()
                  .proxy("POST", "rca/report", null, rcaRequestBody(), AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      verify(rootCauseService, times(1)).getRootCause(eq(PROJECT_ID), eq("checkout"), eq(ANALYSIS_DATE));
      verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldRequeryMysqlAndUpstreamOnEachRequestWhenDaoMisses() {
      when(rcaReportCacheDao.get(any(), any(), any())).thenReturn(Maybe.empty());
      when(rootCauseService.getRootCause(any(), any(), any()))
          .thenReturn(
              Single.just(
                  RootCauseResult.builder().segments(List.of()).baseline(Map.of()).build()));
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  mockBufferedResponse(200, "application/json", "{\"once\":true}")));

      AiProxyServiceImpl service = fullPipelineService();
      String body = rcaRequestBody();

      awaitResult(service.proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));
      awaitResult(service.proxy("POST", "rca/report", null, body, AUTH, PROJECT_ID));

      verify(rootCauseService, times(2)).getRootCause(any(), any(), any());
      verify(httpClient, times(2)).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
      verify(rcaReportCacheDao, times(2)).get(PROJECT_ID, "checkout", ANALYSIS_DATE);
    }
  }

  @Nested
  class ServiceKeyHeader {

    @Test
    void shouldSendXPulseServiceKeyOnOutboundRequest() {
      AiProxyServiceImpl service = fullPipelineServiceWithServiceKey("secret-key");
      when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
          .thenReturn(
              CompletableFuture.completedFuture(
                  mockBufferedResponse(200, "application/json", "{}")));

      awaitResult(service.proxy("GET", "health", null, null, AUTH, PROJECT_ID));

      ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
      verify(httpClient).sendAsync(captor.capture(), any(HttpResponse.BodyHandler.class));
      Optional<String> keyHeader = captor.getValue().headers().firstValue("X-Pulse-Service-Key");
      assertThat(keyHeader).hasValue("secret-key");
    }
  }
}

package org.dreamhorizon.pulseserver.service.ai.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcareport.RcaReportCacheDao;
import org.dreamhorizon.pulseserver.dao.rcareport.models.RcaReportCacheHit;
import org.dreamhorizon.pulseserver.service.ai.AiProxyUpstreamResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InteractionsOverviewProxyHandlerTest {

  private static final String AUTH = "Bearer test-token";
  private static final String PROJECT_ID = "proj-123";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String UPSTREAM_BODY_WITH_CONTEXT =
      "{\"summary\":\"all good\",\"context\":\"ctx-value-xyz\"}";
  private static final String UPSTREAM_BODY_NO_CONTEXT =
      "{\"summary\":\"all good\"}";

  @Mock
  private AiUpstreamProxyExecutor upstream;

  @Mock
  private RcaReportCacheDao rcaReportCacheDao;

  private ObjectMapper objectMapper;
  private InteractionsOverviewProxyHandler handler;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    handler = new InteractionsOverviewProxyHandler(upstream, objectMapper, rcaReportCacheDao);
    lenient().when(upstream.buildTargetUrl(anyString(), any()))
        .thenAnswer(inv -> "http://ai:8000/" + inv.getArgument(0));
    lenient().when(rcaReportCacheDao.put(any(), any(), any(), any(), any()))
        .thenReturn(Completable.complete());
  }

  private AiProxyUpstreamResult await(CompletionStage<AiProxyUpstreamResult> stage) {
    try {
      return stage.toCompletableFuture().get(5, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private AiProxyUpstreamResult upstreamSuccessResult(String body) {
    return AiProxyUpstreamResult.buffered(200, CONTENT_TYPE_JSON, body);
  }

  @Nested
  class CacheHitTests {

    @Test
    void shouldReturnCachedBodyWithContextStrippedOnFreshHit() throws Exception {
      Instant freshCachedAt = Instant.now().minus(30, ChronoUnit.MINUTES);
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.just(new RcaReportCacheHit(UPSTREAM_BODY_WITH_CONTEXT, freshCachedAt)));

      AiProxyUpstreamResult result = await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      JsonNode node = objectMapper.readTree(result.getBufferedBody());
      assertThat(node.has("context")).isFalse();
      assertThat(node.path("summary").asText()).isEqualTo("all good");
      assertThat(node.path("cached").asBoolean()).isTrue();
      assertThat(node.path("cachedAt").asText()).isNotBlank();
      verify(upstream, never()).executeProxy(any(), any(), any(), any(), any());
      verify(rcaReportCacheDao, never()).put(any(), any(), any(), any(), any());
    }

    @Test
    void shouldCallUpstreamAndPassPreviousContextWhenCacheIsStale() throws Exception {
      Instant staleCachedAt = Instant.now().minus(90, ChronoUnit.MINUTES);
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.just(new RcaReportCacheHit(UPSTREAM_BODY_WITH_CONTEXT, staleCachedAt)));
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));

      AiProxyUpstreamResult result = await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      JsonNode node = objectMapper.readTree(result.getBufferedBody());
      assertThat(node.has("context")).isFalse();

      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(upstream).executeProxy(anyString(), anyString(), bodyCaptor.capture(), eq(AUTH), eq(PROJECT_ID));
      JsonNode upstreamBody = objectMapper.readTree(bodyCaptor.getValue());
      assertThat(upstreamBody.path("previousContext").asText()).isEqualTo("ctx-value-xyz");
    }

    @Test
    void shouldStoreFullBodyWithContextInDatabaseOnStaleCacheHit() throws Exception {
      Instant staleCachedAt = Instant.now().minus(90, ChronoUnit.MINUTES);
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.just(new RcaReportCacheHit(UPSTREAM_BODY_WITH_CONTEXT, staleCachedAt)));
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));

      await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      ArgumentCaptor<String> storedBodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(rcaReportCacheDao).put(
          eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any(), storedBodyCaptor.capture());
      JsonNode storedNode = objectMapper.readTree(storedBodyCaptor.getValue());
      assertThat(storedNode.has("context")).isTrue();
      assertThat(storedNode.path("context").asText()).isEqualTo("ctx-value-xyz");
    }

    @Test
    void shouldTreatMissingContextFieldAsPreviousContextNull() throws Exception {
      Instant staleCachedAt = Instant.now().minus(90, ChronoUnit.MINUTES);
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.just(new RcaReportCacheHit(UPSTREAM_BODY_NO_CONTEXT, staleCachedAt)));
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_NO_CONTEXT)));

      await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(upstream).executeProxy(anyString(), anyString(), bodyCaptor.capture(), eq(AUTH), eq(PROJECT_ID));
      JsonNode upstreamBody = objectMapper.readTree(bodyCaptor.getValue());
      assertThat(upstreamBody.has("previousContext")).isFalse();
    }
  }

  @Nested
  class CacheMissTests {

    @Test
    void shouldCallUpstreamWithNoPreviousContextOnCacheMiss() throws Exception {
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.empty());
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));

      AiProxyUpstreamResult result = await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      JsonNode node = objectMapper.readTree(result.getBufferedBody());
      assertThat(node.has("context")).isFalse();

      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(upstream).executeProxy(anyString(), anyString(), bodyCaptor.capture(), eq(AUTH), eq(PROJECT_ID));
      JsonNode upstreamBody = objectMapper.readTree(bodyCaptor.getValue());
      assertThat(upstreamBody.has("previousContext")).isFalse();
    }

    @Test
    void shouldStoreFullBodyWithContextInDatabaseOnCacheMiss() throws Exception {
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.empty());
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));

      await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      ArgumentCaptor<String> storedBodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(rcaReportCacheDao).put(
          eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any(), storedBodyCaptor.capture());
      JsonNode storedNode = objectMapper.readTree(storedBodyCaptor.getValue());
      assertThat(storedNode.has("context")).isTrue();
      assertThat(storedNode.path("context").asText()).isEqualTo("ctx-value-xyz");
    }
  }

  @Nested
  class RegenerateTests {

    @Test
    void shouldCallUpstreamWithPreviousContextWhenRegenerateTrueAndPriorRowExists() throws Exception {
      Instant freshCachedAt = Instant.now().minus(10, ChronoUnit.MINUTES);
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.just(new RcaReportCacheHit(UPSTREAM_BODY_WITH_CONTEXT, freshCachedAt)));
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));

      String body = "{\"regenerate\":true}";
      AiProxyUpstreamResult result = await(handler.handlePost(null, body, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(upstream).executeProxy(anyString(), anyString(), bodyCaptor.capture(), eq(AUTH), eq(PROJECT_ID));
      JsonNode upstreamBody = objectMapper.readTree(bodyCaptor.getValue());
      assertThat(upstreamBody.path("previousContext").asText()).isEqualTo("ctx-value-xyz");
    }

    @Test
    void shouldCallUpstreamWithNoPreviousContextWhenRegenerateTrueAndNoPriorRow() throws Exception {
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.empty());
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));

      String body = "{\"regenerate\":true}";
      await(handler.handlePost(null, body, AUTH, PROJECT_ID));

      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(upstream).executeProxy(anyString(), anyString(), bodyCaptor.capture(), eq(AUTH), eq(PROJECT_ID));
      JsonNode upstreamBody = objectMapper.readTree(bodyCaptor.getValue());
      assertThat(upstreamBody.has("previousContext")).isFalse();
    }
  }

  @Nested
  class ErrorHandlingTests {

    @Test
    void shouldReturn400WhenProjectIdIsBlank() throws Exception {
      AiProxyUpstreamResult result = await(handler.handlePost(null, null, AUTH, ""));

      assertThat(result.getStatusCode()).isEqualTo(400);
      JsonNode envelope = objectMapper.readTree(result.getBufferedBody());
      assertThat(envelope.path("error").path("code").asText()).isNotBlank();
      verify(rcaReportCacheDao, never()).get(any(), any(), any(), any());
      verify(upstream, never()).executeProxy(any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturn400WhenProjectIdIsNull() throws Exception {
      AiProxyUpstreamResult result = await(handler.handlePost(null, null, AUTH, null));

      assertThat(result.getStatusCode()).isEqualTo(400);
      verify(rcaReportCacheDao, never()).get(any(), any(), any(), any());
    }

    @Test
    void shouldNotWriteCacheAndPropagateErrorWhenUpstreamReturns500() throws Exception {
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.empty());
      AiProxyUpstreamResult upstreamErrorResult =
          AiProxyUpstreamResult.buffered(500, CONTENT_TYPE_JSON, "{\"error\":\"boom\"}");
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(upstreamErrorResult));

      AiProxyUpstreamResult result = await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(500);
      verify(rcaReportCacheDao, never()).put(any(), any(), any(), any(), any());
    }

    @Test
    void shouldDefaultRegenerateToFalseWhenBodyIsMalformedJson() throws Exception {
      Instant freshCachedAt = Instant.now().minus(10, ChronoUnit.MINUTES);
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.just(new RcaReportCacheHit(UPSTREAM_BODY_WITH_CONTEXT, freshCachedAt)));

      // Malformed JSON body → should treat as no-body, not 400
      AiProxyUpstreamResult result =
          await(handler.handlePost(null, "not-valid-json", AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      JsonNode node = objectMapper.readTree(result.getBufferedBody());
      assertThat(node.path("cached").asBoolean()).isTrue();
      verify(upstream, never()).executeProxy(any(), any(), any(), any(), any());
    }
  }

  @Nested
  class ContextStrippingTests {

    @Test
    void shouldStoreBodyWithContextButReturnBodyWithoutContext() throws Exception {
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.empty());
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));

      AiProxyUpstreamResult result = await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      // Stored body has context
      ArgumentCaptor<String> storedBodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(rcaReportCacheDao).put(any(), any(), any(), any(), storedBodyCaptor.capture());
      assertThat(storedBodyCaptor.getValue()).contains("context");

      // Returned body has no context
      JsonNode returnedNode = objectMapper.readTree(result.getBufferedBody());
      assertThat(returnedNode.has("context")).isFalse();
      assertThat(returnedNode.path("summary").asText()).isEqualTo("all good");
    }

    @Test
    void shouldTreatNullContextNodeAsPreviousContextNull() throws Exception {
      Instant staleCachedAt = Instant.now().minus(90, ChronoUnit.MINUTES);
      String bodyWithNullContext = "{\"summary\":\"all good\",\"context\":null}";
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.just(new RcaReportCacheHit(bodyWithNullContext, staleCachedAt)));
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_NO_CONTEXT)));

      await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(upstream).executeProxy(anyString(), anyString(), bodyCaptor.capture(), eq(AUTH), eq(PROJECT_ID));
      JsonNode upstreamBody = objectMapper.readTree(bodyCaptor.getValue());
      assertThat(upstreamBody.has("previousContext")).isFalse();
    }

    @Test
    void shouldTreatBlankContextNodeAsPreviousContextNull() throws Exception {
      Instant staleCachedAt = Instant.now().minus(90, ChronoUnit.MINUTES);
      String bodyWithBlankContext = "{\"summary\":\"all good\",\"context\":\"  \"}";
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.just(new RcaReportCacheHit(bodyWithBlankContext, staleCachedAt)));
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_NO_CONTEXT)));

      await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
      verify(upstream).executeProxy(anyString(), anyString(), bodyCaptor.capture(), eq(AUTH), eq(PROJECT_ID));
      JsonNode upstreamBody = objectMapper.readTree(bodyCaptor.getValue());
      assertThat(upstreamBody.has("previousContext")).isFalse();
    }
  }

  @Nested
  class CacheKeyTests {

    @Test
    void shouldUseEpochSentinelNotCurrentDateAsCacheKey() throws Exception {
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.empty());
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));

      await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      // EPOCH sentinel (1970-01-01) must be used — not today's date
      verify(rcaReportCacheDao).get(
          eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"),
          eq(java.time.LocalDate.EPOCH));
      verify(rcaReportCacheDao).put(
          eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"),
          eq(java.time.LocalDate.EPOCH), anyString());
    }
  }

  @Nested
  class CacheWriteFailureTests {

    @Test
    void shouldReturnSuccessfulResponseEvenWhenCacheWriteFails() throws Exception {
      when(rcaReportCacheDao.get(
              eq(PROJECT_ID), eq(RcaType.INTERACTION_OVERVIEW), eq("all"), any()))
          .thenReturn(Maybe.empty());
      when(upstream.executeProxy(anyString(), anyString(), anyString(), eq(AUTH), eq(PROJECT_ID)))
          .thenReturn(
              java.util.concurrent.CompletableFuture.completedFuture(
                  upstreamSuccessResult(UPSTREAM_BODY_WITH_CONTEXT)));
      // DB write fails
      when(rcaReportCacheDao.put(any(), any(), any(), any(), any()))
          .thenReturn(Completable.error(new RuntimeException("db down")));

      AiProxyUpstreamResult result = await(handler.handlePost(null, null, AUTH, PROJECT_ID));

      assertThat(result.getStatusCode()).isEqualTo(200);
      JsonNode node = objectMapper.readTree(result.getBufferedBody());
      assertThat(node.path("summary").asText()).isEqualTo("all good");
      assertThat(node.has("context")).isFalse();
    }
  }
}

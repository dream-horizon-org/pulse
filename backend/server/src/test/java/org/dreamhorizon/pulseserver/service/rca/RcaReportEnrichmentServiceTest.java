package org.dreamhorizon.pulseserver.service.rca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;
import org.dreamhorizon.pulseserver.service.rootcause.models.EvidenceSession;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.service.rootcause.models.SessionEvidenceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RcaReportEnrichmentServiceTest {

  private static final LocalDate DATE = LocalDate.of(2025, 6, 1);

  @Mock
  private RootCauseService rootCauseService;

  @Mock
  private SessionEvidenceService sessionEvidenceService;

  private RcaReportEnrichmentService service;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    service = new RcaReportEnrichmentService(objectMapper, rootCauseService, sessionEvidenceService);
  }

  @Test
  void shouldEnrichSuccessfullyForOneSegment()
      throws ExecutionException, InterruptedException, TimeoutException {
    RootCauseSegment segment =
        RootCauseSegment.builder()
            .label("s1")
            .dimensions(Map.of("platform", "Android"))
            .metrics(Map.of("error_rate", 0.1))
            .build();
    RootCauseResult rootCause =
        RootCauseResult.builder().segments(List.of(segment)).build();
    when(rootCauseService.getRootCause(eq("p1"), eq("ix"), eq(DATE), any(), eq(false)))
        .thenReturn(Single.just(rootCause));
    when(sessionEvidenceService.getSessionEvidence(
            eq("p1"),
            eq("ix"),
            any(),
            any(),
            eq(segment.getDimensions()),
            any(),
            eq(2)))
        .thenReturn(
            Single.just(
                SessionEvidenceResult.builder()
                    .sessions(List.of(EvidenceSession.builder().sessionId("sess-1").build()))
                    .build()));

    ObjectNode body = objectMapper.createObjectNode();
    body.put("entityKey", "ix");
    body.put("date", "2025-06-01");
    RcaParsedReportBody parsed =
        new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, false);

    CompletableFuture<RcaEnrichmentOutcome> done =
        service.enrichAsync(parsed, false).toCompletableFuture();

    RcaEnrichmentOutcome outcome = done.get(5, TimeUnit.SECONDS);
    assertThat(outcome.enrichmentOk()).isTrue();
  }

  @Nested
  class SkipSessionEvidence {

    @Test
    void shouldSkipSessionEvidenceWhenCachedRootCauseAlreadyHasSessions()
        throws ExecutionException, InterruptedException, TimeoutException {
      // cachedAt != null → isCachedFromStore = true; segment has sessions → skip
      RootCauseSegment seg =
          RootCauseSegment.builder()
              .label("s1")
              .exampleSessionIds(List.of("existing-sess"))
              .build();
      RootCauseResult rootCause =
          RootCauseResult.builder().segments(List.of(seg)).cachedAt(Instant.now()).build();
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(rootCause));

      ObjectNode body = objectMapper.createObjectNode();
      body.put("entityKey", "ix");
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      RcaEnrichmentOutcome outcome =
          service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      assertThat(outcome.enrichmentOk()).isTrue();
      verifyNoInteractions(sessionEvidenceService);
    }

    @Test
    void shouldQuerySessionEvidenceWhenCachedRootCauseHasNoSessions()
        throws ExecutionException, InterruptedException, TimeoutException {
      // cachedAt != null but segment has NO sessions → must still query
      RootCauseSegment seg =
          RootCauseSegment.builder()
              .label("s1")
              .dimensions(Map.of("platform", "iOS"))
              .metrics(Map.of())
              .exampleSessionIds(List.of()) // empty
              .build();
      RootCauseResult rootCause =
          RootCauseResult.builder().segments(List.of(seg)).cachedAt(Instant.now()).build();
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(rootCause));
      when(sessionEvidenceService.getSessionEvidence(any(), any(), any(), any(), any(), any(), anyInt()))
          .thenReturn(
              Single.just(
                  SessionEvidenceResult.builder()
                      .sessions(List.of(EvidenceSession.builder().sessionId("s-new").build()))
                      .build()));

      ObjectNode body = objectMapper.createObjectNode();
      body.put("entityKey", "ix");
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      RcaEnrichmentOutcome outcome =
          service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      assertThat(outcome.enrichmentOk()).isTrue();
      assertThat(outcome.body()).contains("s-new");
    }
  }

  @Nested
  class MultipleSegments {

    @Test
    void shouldCollectSessionsForAllSegmentsConcurrently()
        throws ExecutionException, InterruptedException, TimeoutException {
      RootCauseSegment seg1 =
          RootCauseSegment.builder()
              .label("s1")
              .dimensions(Map.of("platform", "Android"))
              .metrics(Map.of("error_rate", 0.2))
              .build();
      RootCauseSegment seg2 =
          RootCauseSegment.builder()
              .label("s2")
              .dimensions(Map.of("platform", "iOS"))
              .metrics(Map.of("apdex", 0.7))
              .build();

      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(RootCauseResult.builder().segments(List.of(seg1, seg2)).build()));
      when(sessionEvidenceService.getSessionEvidence(
              any(), any(), any(), any(), eq(seg1.getDimensions()), any(), anyInt()))
          .thenReturn(
              Single.just(
                  SessionEvidenceResult.builder()
                      .sessions(List.of(EvidenceSession.builder().sessionId("sess-a1").build()))
                      .build()));
      when(sessionEvidenceService.getSessionEvidence(
              any(), any(), any(), any(), eq(seg2.getDimensions()), any(), anyInt()))
          .thenReturn(
              Single.just(
                  SessionEvidenceResult.builder()
                      .sessions(List.of(EvidenceSession.builder().sessionId("sess-b1").build()))
                      .build()));

      ObjectNode body = objectMapper.createObjectNode();
      body.put("entityKey", "ix");
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      RcaEnrichmentOutcome outcome =
          service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      assertThat(outcome.enrichmentOk()).isTrue();
      assertThat(outcome.body()).contains("sess-a1").contains("sess-b1");
    }
  }

  @Nested
  class FallbackPaths {

    @Test
    void shouldReturnFallbackBodyWhenRootCauseSegmentsIsNull() throws Exception {
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(RootCauseResult.builder().segments(null).build()));

      String rawBody = "{\"interactionName\":\"ix\"}";
      ObjectNode body = (ObjectNode) objectMapper.readTree(rawBody);
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(rawBody, body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      RcaEnrichmentOutcome outcome =
          service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      assertThat(outcome.enrichmentOk()).isFalse();
      assertThat(outcome.body()).isEqualTo(rawBody);
    }

    @Test
    void shouldReturnFallbackBodyWhenRootCauseFails() throws Exception {
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.error(new RuntimeException("connection refused")));

      String rawBody = "{\"interactionName\":\"ix\"}";
      ObjectNode body = (ObjectNode) objectMapper.readTree(rawBody);
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(rawBody, body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      RcaEnrichmentOutcome outcome =
          service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      assertThat(outcome.enrichmentOk()).isFalse();
      assertThat(outcome.body()).isEqualTo(rawBody);
      assertThat(outcome.rootCause()).isNull();
    }

    @Test
    void shouldCompleteWhenSessionEvidenceQueryFails()
        throws ExecutionException, InterruptedException, TimeoutException {
      RootCauseSegment segment =
          RootCauseSegment.builder()
              .label("s1")
              .dimensions(Map.of("platform", "Android"))
              .metrics(Map.of())
              .build();
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(RootCauseResult.builder().segments(List.of(segment)).build()));
      when(sessionEvidenceService.getSessionEvidence(any(), any(), any(), any(), any(), any(), anyInt()))
          .thenReturn(Single.error(new RuntimeException("evidence query failed")));

      ObjectNode body = objectMapper.createObjectNode();
      body.put("entityKey", "ix");
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      // Must complete without exception — falls back to partial working body
      RcaEnrichmentOutcome outcome =
          service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      assertThat(outcome).isNotNull();
    }

    @Test
    void shouldReturnEnrichedBodyWithNoSegmentsWhenSegmentListIsEmpty()
        throws ExecutionException, InterruptedException, TimeoutException {
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(RootCauseResult.builder().segments(List.of()).build()));

      ObjectNode body = objectMapper.createObjectNode();
      body.put("entityKey", "ix");
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      RcaEnrichmentOutcome outcome =
          service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      assertThat(outcome.enrichmentOk()).isTrue();
      verifyNoInteractions(sessionEvidenceService);
    }
  }

  @Nested
  class BodyTransformation {

    @Test
    void shouldStripRegenerateFieldFromEnrichedBody()
        throws ExecutionException, InterruptedException, TimeoutException {
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(RootCauseResult.builder().segments(List.of()).build()));

      ObjectNode body = objectMapper.createObjectNode();
      body.put("entityKey", "ix");
      body.put("regenerate", true);
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, true);

      RcaEnrichmentOutcome outcome =
          service.enrichAsync(parsed, true).toCompletableFuture().get(5, TimeUnit.SECONDS);

      assertThat(outcome.enrichmentOk()).isTrue();
      assertThat(outcome.body()).doesNotContain("\"regenerate\"");
    }
  }

  @Nested
  class MetricExtraction {

    @SuppressWarnings("unchecked")
    @Test
    void shouldConvertIntegerMetricToDouble()
        throws ExecutionException, InterruptedException, TimeoutException {
      // error_rate is an Integer (whole-number JSON value) — objectMapper.convertValue must handle it
      RootCauseSegment segment =
          RootCauseSegment.builder()
              .label("s1")
              .dimensions(Map.of("platform", "Android"))
              .metrics(Map.of("error_rate", 5)) // Integer, not Double
              .build();
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(RootCauseResult.builder().segments(List.of(segment)).build()));
      when(sessionEvidenceService.getSessionEvidence(any(), any(), any(), any(), any(), any(), anyInt()))
          .thenReturn(
              Single.just(SessionEvidenceResult.builder().sessions(List.of()).build()));

      ObjectNode body = objectMapper.createObjectNode();
      body.put("entityKey", "ix");
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      ArgumentCaptor<Map<String, Double>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
      verify(sessionEvidenceService)
          .getSessionEvidence(any(), any(), any(), any(), any(), metricsCaptor.capture(), anyInt());
      assertThat(metricsCaptor.getValue()).containsEntry("error_rate", 5.0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldHandleBothErrorRateAndApdexMetrics()
        throws ExecutionException, InterruptedException, TimeoutException {
      RootCauseSegment segment =
          RootCauseSegment.builder()
              .label("s1")
              .dimensions(Map.of("platform", "Android"))
              .metrics(Map.of("error_rate", 0.15, "apdex", 0.8))
              .build();
      when(rootCauseService.getRootCause(any(), any(), any(), any(), anyBoolean()))
          .thenReturn(Single.just(RootCauseResult.builder().segments(List.of(segment)).build()));
      when(sessionEvidenceService.getSessionEvidence(any(), any(), any(), any(), any(), any(), anyInt()))
          .thenReturn(
              Single.just(SessionEvidenceResult.builder().sessions(List.of()).build()));

      ObjectNode body = objectMapper.createObjectNode();
      body.put("entityKey", "ix");
      RcaParsedReportBody parsed =
          new RcaParsedReportBody(body.toString(), body, "p1", RcaType.INTERACTION, "ix", DATE, false);

      service.enrichAsync(parsed, false).toCompletableFuture().get(5, TimeUnit.SECONDS);

      ArgumentCaptor<Map<String, Double>> metricsCaptor = ArgumentCaptor.forClass(Map.class);
      verify(sessionEvidenceService)
          .getSessionEvidence(any(), any(), any(), any(), any(), metricsCaptor.capture(), anyInt());
      assertThat(metricsCaptor.getValue())
          .containsEntry("error_rate", 0.15)
          .containsEntry("apdex", 0.8);
    }
  }
}

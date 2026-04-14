package org.dreamhorizon.pulseserver.service.rca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.SessionEvidenceService;
import org.dreamhorizon.pulseserver.service.rootcause.models.EvidenceSession;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.service.rootcause.models.SessionEvidenceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
    objectMapper = new ObjectMapper();
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
    body.put("interactionName", "ix");
    body.put("date", "2025-06-01");
    RcaParsedReportBody parsed =
        new RcaParsedReportBody(body.toString(), body, "p1", "ix", DATE, false);

    CompletableFuture<RcaEnrichmentOutcome> done =
        service.enrichAsync(parsed, false).toCompletableFuture();

    RcaEnrichmentOutcome outcome = done.get(5, TimeUnit.SECONDS);
    assertThat(outcome.enrichmentOk()).isTrue();
  }
}

package org.dreamhorizon.pulseserver.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.dreamhorizon.pulseserver.dao.sessiondetail.SessionDetailDao;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.InteractionRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.NetworkRequestRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionCoreRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionExceptionRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionSpanRow;
import org.dreamhorizon.pulseserver.dao.sessiondetail.models.SessionTimingRow;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionDetailServiceTest {

  private static final String SESSION_ID = "sess-abc-123";

  @Mock
  SessionDetailDao sessionDetailDao;

  SessionDetailService sessionDetailService;

  @BeforeEach
  void setUp() {
    sessionDetailService = new SessionDetailService(sessionDetailDao);
  }

  private static QueryResultResponse<SessionCoreRow> coreResponse(List<SessionCoreRow> rows) {
    return QueryResultResponse.<SessionCoreRow>builder().rows(rows).build();
  }

  private static QueryResultResponse<SessionTimingRow> timingResponse(List<SessionTimingRow> rows) {
    return QueryResultResponse.<SessionTimingRow>builder().rows(rows).build();
  }

  private static QueryResultResponse<InteractionRow> interactionResponse(List<InteractionRow> rows) {
    return QueryResultResponse.<InteractionRow>builder().rows(rows).build();
  }

  private static QueryResultResponse<NetworkRequestRow> networkResponse(List<NetworkRequestRow> rows) {
    return QueryResultResponse.<NetworkRequestRow>builder().rows(rows).build();
  }

  private static QueryResultResponse<SessionExceptionRow> exceptionResponse(List<SessionExceptionRow> rows) {
    return QueryResultResponse.<SessionExceptionRow>builder().rows(rows).build();
  }

  private static QueryResultResponse<SessionSpanRow> spanResponse(List<SessionSpanRow> rows) {
    return QueryResultResponse.<SessionSpanRow>builder().rows(rows).build();
  }

  @Nested
  class GetSessionDetail {

    @Test
    void shouldReturnErrorWhenSessionIdIsNull() {
      sessionDetailService.getSessionDetail(null, Collections.emptySet())
          .test()
          .assertError(throwable ->
              throwable.getMessage() != null && throwable.getMessage().contains("sessionId"));
    }

    @Test
    void shouldReturnErrorWhenSessionIdIsBlank() {
      sessionDetailService.getSessionDetail("  ", Collections.emptySet())
          .test()
          .assertError(throwable ->
              throwable.getMessage() != null && throwable.getMessage().contains("sessionId"));
    }

    @Test
    void shouldReturnSessionDetailWithCoreAndTimingOnly() {
      SessionCoreRow core = SessionCoreRow.builder()
          .sessionId(SESSION_ID)
          .userId("user-1")
          .platform("android")
          .device("Pixel 6")
          .osVersion("14")
          .appVersion("1.2.0")
          .geography("US")
          .qualityScore(0.95)
          .journey(null)
          .build();
      SessionTimingRow timing = SessionTimingRow.builder()
          .sessionId(SESSION_ID)
          .sessionStart("2024-01-15T10:00:00Z")
          .sessionEnd("2024-01-15T10:05:00Z")
          .durationMs(300_000)
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Collections.emptySet()).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
      assertThat(result.getUserId()).isEqualTo("user-1");
      assertThat(result.isAnonymous()).isFalse();
      assertThat(result.getStartTime()).isEqualTo("2024-01-15T10:00:00Z");
      assertThat(result.getEndTime()).isEqualTo("2024-01-15T10:05:00Z");
      assertThat(result.getDuration()).isEqualTo(300_000);
      assertThat(result.getPlatform()).isEqualTo("android");
      assertThat(result.getDevice()).isEqualTo("Pixel 6");
      assertThat(result.getQuality()).isEqualTo(0.95);
      assertThat(result.getInteractions()).isEmpty();
      assertThat(result.getNetworkRequests()).isEmpty();
      assertThat(result.getEvents()).isNull();
      assertThat(result.getExceptions()).isNull();

      verify(sessionDetailDao).getSessionCore(SESSION_ID);
      verify(sessionDetailDao).getSessionTiming(SESSION_ID);
      verify(sessionDetailDao).getInteractions(SESSION_ID);
      verify(sessionDetailDao).getNetworkRequests(SESSION_ID);
    }

    @Test
    void shouldIncludeInteractionsAndNetworkInResponse() {
      SessionCoreRow core = SessionCoreRow.builder().sessionId(SESSION_ID).qualityScore(0).build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();
      InteractionRow interaction = InteractionRow.builder()
          .interactionName("button_click")
          .successCount(10)
          .failureCount(0)
          .avgDurationMs(50.5)
          .apdexScore(1.0)
          .build();
      NetworkRequestRow network = NetworkRequestRow.builder()
          .timestamp("2024-01-15T10:01:00Z")
          .durationNs(100_000_000L)
          .httpMethod("GET")
          .httpUrl("https://api.example.com/users")
          .httpStatusCode("200")
          .httpTarget("/users")
          .traceId("trace-1")
          .spanId("span-1")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(List.of(interaction))));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(List.of(network))));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result.getInteractions()).hasSize(1);
      assertThat(result.getInteractions().get(0).getInteractionName()).isEqualTo("button_click");
      assertThat(result.getInteractions().get(0).getStatus()).isEqualTo("success");
      assertThat(result.getInteractions().get(0).getDurationMs()).isEqualTo(50.5);

      assertThat(result.getNetworkRequests()).hasSize(1);
      assertThat(result.getNetworkRequests().get(0).getUrl()).isEqualTo("https://api.example.com/users");
      assertThat(result.getNetworkRequests().get(0).getMethod()).isEqualTo("GET");
      assertThat(result.getNetworkRequests().get(0).getTraceId()).isEqualTo("trace-1");
    }

    @Test
    void shouldIncludeExceptionsWhenIncludeSectionsContainsExceptions() {
      SessionCoreRow core = SessionCoreRow.builder().sessionId(SESSION_ID).qualityScore(0).build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();
      SessionExceptionRow exception = SessionExceptionRow.builder()
          .timestamp("2024-01-15T10:02:00Z")
          .pulseType("crash")
          .title("NullPointerException")
          .exceptionStackTrace("at com.example.Main.onCreate")
          .traceId("trace-2")
          .spanId("span-2")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));
      when(sessionDetailDao.getExceptions(eq(SESSION_ID))).thenReturn(Single.just(exceptionResponse(List.of(exception))));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of("exceptions")).blockingGet();

      assertThat(result.getExceptions()).hasSize(1);
      assertThat(result.getExceptions().get(0).getPulseType()).isEqualTo("crash");
      assertThat(result.getExceptions().get(0).getTitle()).isEqualTo("NullPointerException");
      assertThat(result.getExceptions().get(0).getExceptionStackTrace()).contains("at com.example.Main.onCreate");
    }

    @Test
    void shouldIncludeEventsWhenIncludeSectionsContainsEvents() {
      SessionCoreRow core = SessionCoreRow.builder().sessionId(SESSION_ID).qualityScore(0).build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();
      SessionSpanRow span = SessionSpanRow.builder()
          .timestamp("2024-01-15T10:01:30Z")
          .eventType("navigation")
          .description("Screen: Home")
          .durationNs(50_000_000L)
          .traceId("trace-3")
          .spanId("span-3")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));
      when(sessionDetailDao.getExceptions(eq(SESSION_ID))).thenReturn(Single.just(exceptionResponse(Collections.emptyList())));
      when(sessionDetailDao.getEventSpans(eq(SESSION_ID))).thenReturn(Single.just(spanResponse(List.of(span))));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of("events")).blockingGet();

      assertThat(result.getEvents()).isNotNull();
      assertThat(result.getEvents()).hasSize(1);
      assertThat(result.getEvents().get(0).getTimestamp()).isEqualTo("2024-01-15T10:01:30Z");
      assertThat(result.getEvents().get(0).getEventType().getValue()).isEqualTo("navigation");
      assertThat(result.getEvents().get(0).getDescription()).isEqualTo("Screen: Home");
    }

    @Test
    void shouldMapInteractionStatusToPartialWhenBothSuccessAndFailure() {
      SessionCoreRow core = SessionCoreRow.builder().sessionId(SESSION_ID).qualityScore(0).build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();
      InteractionRow interaction = InteractionRow.builder()
          .interactionName("api_call")
          .successCount(5)
          .failureCount(2)
          .avgDurationMs(100.0)
          .apdexScore(0.8)
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(List.of(interaction))));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result.getInteractions().get(0).getStatus()).isEqualTo("partial");
    }

    @Test
    void shouldMapInteractionStatusToFailedWhenOnlyFailures() {
      SessionCoreRow core = SessionCoreRow.builder().sessionId(SESSION_ID).qualityScore(0).build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();
      InteractionRow interaction = InteractionRow.builder()
          .interactionName("login")
          .successCount(0)
          .failureCount(3)
          .avgDurationMs(0)
          .apdexScore(0)
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(List.of(interaction))));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result.getInteractions().get(0).getStatus()).isEqualTo("failed");
    }

    @Test
    void shouldUseDefaultCoreAndTimingWhenDaoReturnsEmptyRows() {
      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(Collections.emptyList())));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(Collections.emptyList())));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
      assertThat(result.getDuration()).isEqualTo(0);
      assertThat(result.getQuality()).isEqualTo(0);
      assertThat(result.isAnonymous()).isTrue();
    }

    @Test
    void shouldParseJourneyJsonWhenCoreHasJourney() {
      SessionCoreRow core = SessionCoreRow.builder()
          .sessionId(SESSION_ID)
          .qualityScore(0)
          .journey("[\"screen_a\",\"screen_b\",\"screen_c\"]")
          .build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result.getJourney()).containsExactly("screen_a", "screen_b", "screen_c");
    }

    @Test
    void shouldReturnEmptyJourneyWhenJourneyJsonInvalid() {
      SessionCoreRow core = SessionCoreRow.builder()
          .sessionId(SESSION_ID)
          .qualityScore(0)
          .journey("{invalid json not array")
          .build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result.getJourney()).isEmpty();
    }

    @Test
    void shouldHandleNullRowsFromDao() {
      QueryResultResponse<SessionCoreRow> coreWithNullRows = QueryResultResponse.<SessionCoreRow>builder().build();
      QueryResultResponse<SessionTimingRow> timingWithNullRows = QueryResultResponse.<SessionTimingRow>builder().build();
      QueryResultResponse<InteractionRow> interactionWithNullRows = QueryResultResponse.<InteractionRow>builder().build();
      QueryResultResponse<NetworkRequestRow> networkWithNullRows = QueryResultResponse.<NetworkRequestRow>builder().build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreWithNullRows));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingWithNullRows));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionWithNullRows));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkWithNullRows));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
      assertThat(result.getInteractions()).isEmpty();
      assertThat(result.getNetworkRequests()).isEmpty();
    }

    @Test
    void shouldMergeAndSortEventsByTimestampWhenIncludeEvents() {
      SessionCoreRow core = SessionCoreRow.builder().sessionId(SESSION_ID).qualityScore(0).build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();
      SessionSpanRow span = SessionSpanRow.builder()
          .timestamp("2024-01-15T10:02:00Z")
          .eventType("navigation")
          .description("Screen B")
          .durationNs(0)
          .traceId("t2")
          .spanId("s2")
          .build();
      NetworkRequestRow network = NetworkRequestRow.builder()
          .timestamp("2024-01-15T10:01:00Z")
          .durationNs(100L)
          .description("GET /api")
          .traceId("t1")
          .spanId("s1")
          .build();
      SessionExceptionRow exception = SessionExceptionRow.builder()
          .timestamp("2024-01-15T10:03:00Z")
          .pulseType("crash")
          .title("Error")
          .traceId("t3")
          .spanId("s3")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(List.of(network))));
      when(sessionDetailDao.getExceptions(eq(SESSION_ID))).thenReturn(Single.just(exceptionResponse(List.of(exception))));
      when(sessionDetailDao.getEventSpans(eq(SESSION_ID))).thenReturn(Single.just(spanResponse(List.of(span))));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of("events")).blockingGet();

      assertThat(result.getEvents()).hasSize(3);
      assertThat(result.getEvents().get(0).getTimestamp()).isEqualTo("2024-01-15T10:01:00Z");
      assertThat(result.getEvents().get(0).getEventType().getValue()).isEqualTo("api_call");
      assertThat(result.getEvents().get(1).getTimestamp()).isEqualTo("2024-01-15T10:02:00Z");
      assertThat(result.getEvents().get(1).getDescription()).isEqualTo("Screen B");
      assertThat(result.getEvents().get(2).getTimestamp()).isEqualTo("2024-01-15T10:03:00Z");
      assertThat(result.getEvents().get(2).getDescription()).isEqualTo("Error");
    }

    @Test
    void shouldIncludeBothEventsAndExceptionsSectionsWhenBothRequested() {
      SessionCoreRow core = SessionCoreRow.builder().sessionId(SESSION_ID).qualityScore(0).build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();
      SessionExceptionRow exception = SessionExceptionRow.builder()
          .timestamp("2024-01-15T10:00:00Z")
          .pulseType("anr")
          .title("ANR detected")
          .exceptionStackTrace("at android.app.ActivityThread")
          .traceId("tr")
          .spanId("sp")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));
      when(sessionDetailDao.getExceptions(eq(SESSION_ID))).thenReturn(Single.just(exceptionResponse(List.of(exception))));
      when(sessionDetailDao.getEventSpans(eq(SESSION_ID))).thenReturn(Single.just(spanResponse(Collections.emptyList())));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of("events", "exceptions")).blockingGet();

      assertThat(result.getExceptions()).hasSize(1);
      assertThat(result.getExceptions().get(0).getPulseType()).isEqualTo("anr");
      assertThat(result.getExceptions().get(0).getTitle()).isEqualTo("ANR detected");
      assertThat(result.getEvents()).hasSize(1);
      assertThat(result.getEvents().get(0).getEventType().getValue()).isEqualTo("anr");
      assertThat(result.getEvents().get(0).getDescription()).isEqualTo("ANR detected");
    }

    @Test
    void shouldMapSpanWithUnknownEventTypeToNullEventType() {
      SessionCoreRow core = SessionCoreRow.builder().sessionId(SESSION_ID).qualityScore(0).build();
      SessionTimingRow timing = SessionTimingRow.builder().sessionId(SESSION_ID).durationMs(0).build();
      SessionSpanRow span = SessionSpanRow.builder()
          .timestamp("2024-01-15T10:00:00Z")
          .eventType("unknown_type")
          .description("Custom")
          .durationNs(0)
          .traceId("t")
          .spanId("s")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID))).thenReturn(Single.just(coreResponse(List.of(core))));
      when(sessionDetailDao.getSessionTiming(eq(SESSION_ID))).thenReturn(Single.just(timingResponse(List.of(timing))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID))).thenReturn(Single.just(interactionResponse(Collections.emptyList())));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID))).thenReturn(Single.just(networkResponse(Collections.emptyList())));
      when(sessionDetailDao.getExceptions(eq(SESSION_ID))).thenReturn(Single.just(exceptionResponse(Collections.emptyList())));
      when(sessionDetailDao.getEventSpans(eq(SESSION_ID))).thenReturn(Single.just(spanResponse(List.of(span))));

      SessionDetailResponse result = sessionDetailService.getSessionDetail(SESSION_ID, Set.of("events")).blockingGet();

      assertThat(result.getEvents()).hasSize(1);
      assertThat(result.getEvents().get(0).getEventType()).isNull();
      assertThat(result.getEvents().get(0).getDescription()).isEqualTo("Custom");
    }
  }
}

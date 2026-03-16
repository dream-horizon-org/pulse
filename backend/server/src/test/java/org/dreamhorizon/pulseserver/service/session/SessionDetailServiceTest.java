package org.dreamhorizon.pulseserver.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import jakarta.ws.rs.WebApplicationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionDetailServiceTest {

  private static final String SESSION_ID = "sess-detail-123";

  @Mock
  SessionDetailDao sessionDetailDao;

  SessionDetailService sessionDetailService;

  @BeforeEach
  void setUp() {
    sessionDetailService = new SessionDetailService(sessionDetailDao);
  }

  private SessionCoreRow coreRow(String sessionId) {
    return SessionCoreRow.builder()
        .sessionId(sessionId)
        .userId("user-1")
        .platform("android")
        .device("Pixel 6")
        .osVersion("14")
        .appVersion("1.0.0")
        .sessionStart("2024-01-01T10:00:00Z")
        .sessionEnd("2024-01-01T10:05:00Z")
        .durationMs(300_000L)
        .geography("IN")
        .qualityScore(0.95)
        .journey(null)
        .build();
  }

  private QueryResultResponse<SessionCoreRow> coreResult(SessionCoreRow row) {
    return QueryResultResponse.<SessionCoreRow>builder()
        .rows(List.of(row))
        .build();
  }

  private QueryResultResponse<SessionCoreRow> emptyCoreResult() {
    return QueryResultResponse.<SessionCoreRow>builder()
        .rows(Collections.emptyList())
        .build();
  }

  @Nested
  class GetSessionDetail {

    @Test
    void shouldReturnSessionDetailWhenSessionExists() {
      when(sessionDetailDao.getSessionCore(eq(SESSION_ID)))
          .thenReturn(Single.just(coreResult(coreRow(SESSION_ID))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<InteractionRow>builder()
              .rows(Collections.emptyList())
              .build()));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<NetworkRequestRow>builder()
              .rows(Collections.emptyList())
              .build()));

      SessionDetailResponse result =
          sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getSessionId()).isEqualTo(SESSION_ID);
      assertThat(result.getUserId()).isEqualTo("user-1");
      assertThat(result.isAnonymous()).isFalse();
      assertThat(result.getPlatform()).isEqualTo("android");
      assertThat(result.getDevice()).isEqualTo("Pixel 6");
      assertThat(result.getDuration()).isEqualTo(300_000L);
      assertThat(result.getInteractions()).isEmpty();
      assertThat(result.getNetworkRequests()).isEmpty();
      assertThat(result.getEvents()).isNull();
      assertThat(result.getExceptions()).isNull();

      verify(sessionDetailDao).getSessionCore(SESSION_ID);
      verify(sessionDetailDao).getInteractions(SESSION_ID);
      verify(sessionDetailDao).getNetworkRequests(SESSION_ID);
    }

    @Test
    void shouldMapInteractionsAndNetworkRequests() {
      InteractionRow interaction = InteractionRow.builder()
          .interactionName("button_tap")
          .successCount(2L)
          .failureCount(0L)
          .avgDurationMs(50.0)
          .apdexScore(1.0)
          .build();
      NetworkRequestRow network = NetworkRequestRow.builder()
          .timestamp("2024-01-01T10:01:00Z")
          .durationNs(100_000_000L)
          .httpMethod("GET")
          .httpUrl("https://api.example.com/data")
          .httpStatusCode("200")
          .httpTarget("api.example.com")
          .traceId("trace-1")
          .spanId("span-1")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID)))
          .thenReturn(Single.just(coreResult(coreRow(SESSION_ID))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<InteractionRow>builder()
              .rows(List.of(interaction))
              .build()));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<NetworkRequestRow>builder()
              .rows(List.of(network))
              .build()));

      SessionDetailResponse result =
          sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet();

      assertThat(result.getInteractions()).hasSize(1);
      assertThat(result.getInteractions().get(0).getInteractionName()).isEqualTo("button_tap");
      assertThat(result.getInteractions().get(0).getStatus()).isEqualTo("success");
      assertThat(result.getNetworkRequests()).hasSize(1);
      assertThat(result.getNetworkRequests().get(0).getUrl()).isEqualTo("https://api.example.com/data");
      assertThat(result.getNetworkRequests().get(0).getStatus()).isEqualTo("200");
    }

    @Test
    void shouldErrorWhenSessionIdIsBlank() {
      assertThatThrownBy(() ->
          sessionDetailService.getSessionDetail("  ", Set.of()).blockingGet())
          .hasCauseInstanceOf(WebApplicationException.class);
    }

    @Test
    void shouldErrorWhenSessionIdIsNull() {
      assertThatThrownBy(() ->
          sessionDetailService.getSessionDetail(null, Set.of()).blockingGet())
          .hasCauseInstanceOf(WebApplicationException.class);
    }

    @Test
    void shouldErrorWhenSessionNotFound() {
      when(sessionDetailDao.getSessionCore(eq(SESSION_ID)))
          .thenReturn(Single.just(emptyCoreResult()));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<InteractionRow>builder()
              .rows(Collections.emptyList())
              .build()));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<NetworkRequestRow>builder()
              .rows(Collections.emptyList())
              .build()));

      assertThatThrownBy(() ->
          sessionDetailService.getSessionDetail(SESSION_ID, Set.of()).blockingGet())
          .hasCauseInstanceOf(WebApplicationException.class);
    }

    @Test
    void shouldIncludeEventsAndExceptionsWhenIncludeParamHasEvents() {
      SessionSpanRow span = SessionSpanRow.builder()
          .timestamp("2024-01-01T10:01:00Z")
          .eventType("screen_view")
          .description("HomeScreen")
          .durationNs(0L)
          .traceId("t1")
          .spanId("s1")
          .build();
      SessionExceptionRow exc = SessionExceptionRow.builder()
          .timestamp("2024-01-01T10:02:00Z")
          .pulseType("java_crash")
          .title("NullPointerException")
          .exceptionStackTrace("at com.app.Main")
          .traceId("t2")
          .spanId("s2")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID)))
          .thenReturn(Single.just(coreResult(coreRow(SESSION_ID))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<InteractionRow>builder()
              .rows(Collections.emptyList())
              .build()));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<NetworkRequestRow>builder()
              .rows(Collections.emptyList())
              .build()));
      when(sessionDetailDao.getEventSpans(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<SessionSpanRow>builder()
              .rows(List.of(span))
              .build()));
      when(sessionDetailDao.getExceptions(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<SessionExceptionRow>builder()
              .rows(List.of(exc))
              .build()));

      SessionDetailResponse result =
          sessionDetailService.getSessionDetail(SESSION_ID, Set.of("events")).blockingGet();

      assertThat(result.getEvents()).isNotNull();
      assertThat(result.getEvents()).hasSize(2);
      assertThat(result.getExceptions()).isNull();

      verify(sessionDetailDao).getEventSpans(SESSION_ID);
      verify(sessionDetailDao).getExceptions(SESSION_ID);
    }

    @Test
    void shouldIncludeOnlyExceptionsWhenIncludeParamHasExceptions() {
      SessionExceptionRow exc = SessionExceptionRow.builder()
          .timestamp("2024-01-01T10:02:00Z")
          .pulseType("java_crash")
          .title("NPE")
          .exceptionStackTrace("at com.app.Main")
          .traceId("t2")
          .spanId("s2")
          .build();

      when(sessionDetailDao.getSessionCore(eq(SESSION_ID)))
          .thenReturn(Single.just(coreResult(coreRow(SESSION_ID))));
      when(sessionDetailDao.getInteractions(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<InteractionRow>builder()
              .rows(Collections.emptyList())
              .build()));
      when(sessionDetailDao.getNetworkRequests(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<NetworkRequestRow>builder()
              .rows(Collections.emptyList())
              .build()));
      when(sessionDetailDao.getExceptions(eq(SESSION_ID)))
          .thenReturn(Single.just(QueryResultResponse.<SessionExceptionRow>builder()
              .rows(List.of(exc))
              .build()));

      SessionDetailResponse result =
          sessionDetailService.getSessionDetail(SESSION_ID, Set.of("exceptions")).blockingGet();

      assertThat(result.getExceptions()).isNotNull().hasSize(1);
      assertThat(result.getExceptions().get(0).getTitle()).isEqualTo("NPE");
      assertThat(result.getEvents()).isNull();

      verify(sessionDetailDao).getExceptions(SESSION_ID);
    }
  }
}

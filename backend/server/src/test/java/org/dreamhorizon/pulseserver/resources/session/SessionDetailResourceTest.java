package org.dreamhorizon.pulseserver.resources.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SessionJourneyResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.session.SessionDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionDetailResourceTest {

  private static final String SESSION_ID = "sess-xyz-789";

  @Mock
  SessionDetailService sessionDetailService;

  SessionDetailResource resource;

  @BeforeEach
  void setUp() {
    resource = new SessionDetailResource(sessionDetailService);
  }

  @Nested
  class GetSessionDetail {

    @Test
    void shouldReturnSessionDetailSuccessfully(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        SessionDetailResponse response = SessionDetailResponse.builder()
            .sessionId(SESSION_ID)
            .userId("user-1")
            .duration(5000L)
            .platform("android")
            .build();

        when(sessionDetailService.getSessionDetail(eq(SESSION_ID), eq(Collections.emptySet()), isNull(), isNull(), isNull()))
            .thenReturn(Single.just(response));

        CompletionStage<Response<SessionDetailResponse>> result = resource.getSessionDetail(SESSION_ID, null, null, null, null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(resp).isNotNull();
            assertThat(resp.getData()).isNotNull();
            assertThat(resp.getData().getSessionId()).isEqualTo(SESSION_ID);
            assertThat(resp.getData().getUserId()).isEqualTo("user-1");
            assertThat(resp.getData().getDuration()).isEqualTo(5000L);
            verify(sessionDetailService).getSessionDetail(eq(SESSION_ID), eq(Collections.emptySet()), isNull(), isNull(), isNull());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldParseIncludeParamAndDelegateEventsAndExceptions(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        SessionDetailResponse response = SessionDetailResponse.builder()
            .sessionId(SESSION_ID)
            .events(Collections.emptyList())
            .exceptions(Collections.emptyList())
            .build();

        when(sessionDetailService.getSessionDetail(eq(SESSION_ID), any(), any(), any(), any()))
            .thenReturn(Single.just(response));

        CompletionStage<Response<SessionDetailResponse>> result =
            resource.getSessionDetail(SESSION_ID, "events,exceptions", null, null, null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(resp.getData()).isNotNull();
            assertThat(resp.getData().getEvents()).isEmpty();
            assertThat(resp.getData().getExceptions()).isEmpty();
            verify(sessionDetailService).getSessionDetail(eq(SESSION_ID), any(), any(), any(), any());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldNormalizeIncludeParamToLowerCase(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        when(sessionDetailService.getSessionDetail(eq(SESSION_ID), any(), any(), any(), any()))
            .thenReturn(Single.just(SessionDetailResponse.builder().sessionId(SESSION_ID).build()));

        CompletionStage<Response<SessionDetailResponse>> result =
            resource.getSessionDetail(SESSION_ID, "EVENTS,Exceptions", null, null, null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            verify(sessionDetailService).getSessionDetail(eq(SESSION_ID), eq(Set.of("events", "exceptions")), any(), any(), any());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldIgnoreInvalidIncludeValues(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        when(sessionDetailService.getSessionDetail(eq(SESSION_ID), eq(Set.of("events")), any(), any(), any()))
            .thenReturn(Single.just(SessionDetailResponse.builder().sessionId(SESSION_ID).build()));

        CompletionStage<Response<SessionDetailResponse>> result =
            resource.getSessionDetail(SESSION_ID, "events,invalid,other", null, null, null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            verify(sessionDetailService).getSessionDetail(eq(SESSION_ID), eq(Set.of("events")), any(), any(), any());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldTreatBlankIncludeAsEmptySet(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        when(sessionDetailService.getSessionDetail(eq(SESSION_ID), eq(Collections.emptySet()), any(), any(), any()))
            .thenReturn(Single.just(SessionDetailResponse.builder().sessionId(SESSION_ID).build()));

        CompletionStage<Response<SessionDetailResponse>> result =
            resource.getSessionDetail(SESSION_ID, "  ", null, null, null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            verify(sessionDetailService).getSessionDetail(eq(SESSION_ID), eq(Collections.emptySet()), any(), any(), any());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPropagateServiceError(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        when(sessionDetailService.getSessionDetail(eq(SESSION_ID), any(), any(), any(), any()))
            .thenReturn(Single.error(new IllegalArgumentException("sessionId is required")));

        CompletionStage<Response<SessionDetailResponse>> result = resource.getSessionDetail(SESSION_ID, null, null, null, null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNotNull();
            assertThat(err).isInstanceOf(IllegalArgumentException.class);
            assertThat(err.getMessage()).contains("sessionId");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldForwardStartTimeEndTimeAndDurationMs(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        String startTime = "2024-01-15T10:00:00Z";
        String endTime = "2024-01-15T10:05:00Z";
        Long durationMs = 300_000L;

        when(sessionDetailService.getSessionDetail(
            eq(SESSION_ID), any(), eq(startTime), eq(endTime), eq(durationMs)))
            .thenReturn(Single.just(SessionDetailResponse.builder().sessionId(SESSION_ID).build()));

        CompletionStage<Response<SessionDetailResponse>> result =
            resource.getSessionDetail(SESSION_ID, null, startTime, endTime, durationMs);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            verify(sessionDetailService).getSessionDetail(
                eq(SESSION_ID), any(), eq(startTime), eq(endTime), eq(durationMs));
          });
          testContext.completeNow();
        });
      });
    }
  }

  @Nested
  class GetSessionJourney {

    @Test
    void shouldReturnJourneySuccessfully(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        SessionJourneyResponse response = SessionJourneyResponse.builder()
            .journey(List.of("Home", "Profile", "Settings"))
            .build();

        when(sessionDetailService.getSessionJourney(eq(SESSION_ID), any(), any()))
            .thenReturn(Single.just(response));

        CompletionStage<Response<SessionJourneyResponse>> result =
            resource.getSessionJourney(SESSION_ID, "2024-01-15T10:00:00Z", "2024-01-15T10:05:00Z");

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(resp.getData()).isNotNull();
            assertThat(resp.getData().getJourney()).containsExactly("Home", "Profile", "Settings");
          });
          testContext.completeNow();
        });
      });
    }
  }
}

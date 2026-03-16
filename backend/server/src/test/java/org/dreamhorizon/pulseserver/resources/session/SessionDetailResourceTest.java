package org.dreamhorizon.pulseserver.resources.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.dreamhorizon.pulseserver.resources.session.models.SessionDetailResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.session.SessionDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionDetailResourceTest {

  private static final String SESSION_ID = "sess-api-123";

  @Mock
  SessionDetailService sessionDetailService;

  SessionDetailResource resource;

  @BeforeEach
  void setUp() {
    resource = new SessionDetailResource(sessionDetailService);
  }

  @Test
  void shouldReturn200AndDelegateToService(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      SessionDetailResponse detail = SessionDetailResponse.builder()
          .sessionId(SESSION_ID)
          .userId("user-1")
          .duration(5000L)
          .build();

      when(sessionDetailService.getSessionDetail(eq(SESSION_ID), any()))
          .thenReturn(Single.just(detail));

      CompletionStage<Response<SessionDetailResponse>> result =
          resource.getSessionDetail(SESSION_ID, null);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getSessionId()).isEqualTo(SESSION_ID);
          verify(sessionDetailService).getSessionDetail(eq(SESSION_ID), argThat(
              (Set<String> s) -> s != null && s.isEmpty()));
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldPassIncludeEventsAndExceptionsToService(
      io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      SessionDetailResponse detail = SessionDetailResponse.builder()
          .sessionId(SESSION_ID)
          .build();

      when(sessionDetailService.getSessionDetail(eq(SESSION_ID), argThat(
          (Set<String> s) -> s != null && s.size() == 2 && s.contains("events") && s.contains("exceptions"))))
          .thenReturn(Single.just(detail));

      CompletionStage<Response<SessionDetailResponse>> result =
          resource.getSessionDetail(SESSION_ID, "events,exceptions");

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() ->
            verify(sessionDetailService).getSessionDetail(eq(SESSION_ID), argThat(
                (Set<String> s) -> s != null && s.size() == 2 && s.contains("events") && s.contains("exceptions"))));
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldFilterInvalidIncludeValues(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      SessionDetailResponse detail = SessionDetailResponse.builder()
          .sessionId(SESSION_ID)
          .build();

      when(sessionDetailService.getSessionDetail(eq(SESSION_ID), argThat(
          (Set<String> s) -> s != null && s.size() == 1 && s.contains("events"))))
          .thenReturn(Single.just(detail));

      CompletionStage<Response<SessionDetailResponse>> result =
          resource.getSessionDetail(SESSION_ID, "events,invalid,EVENTS");

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() ->
            verify(sessionDetailService).getSessionDetail(eq(SESSION_ID), argThat(
                (Set<String> s) -> s != null && s.size() == 1 && s.contains("events"))));
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldPropagateErrorFromService(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      when(sessionDetailService.getSessionDetail(eq(SESSION_ID), any()))
          .thenReturn(Single.error(new RuntimeException("DAO error")));

      CompletionStage<Response<SessionDetailResponse>> result =
          resource.getSessionDetail(SESSION_ID, null);

      result.whenComplete((response, error) -> {
        testContext.verify(() -> {
          assertThat(error).isNotNull();
          Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
          assertThat(cause).isInstanceOf(RuntimeException.class);
          assertThat(cause.getMessage()).contains("DAO error");
        });
        testContext.completeNow();
      });
    });
  }
}

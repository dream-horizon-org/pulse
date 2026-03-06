package org.dreamhorizon.pulseserver.resources.breadcrumb.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.sql.Timestamp;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.resources.breadcrumb.models.BreadcrumbRequestDto;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.breadcrumb.BreadcrumbService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class GetSessionBreadcrumbsTest {

  @Mock
  BreadcrumbService breadcrumbService;

  GetSessionBreadcrumbs resource;

  @BeforeEach
  void setUp() {
    resource = new GetSessionBreadcrumbs(breadcrumbService);
  }

  @Test
  void shouldDelegateToServiceAndReturnResponse(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String sessionId = "test-session-123";
      String errorTimestamp = "2026-02-27T15:14:26Z";
      BreadcrumbRequestDto request = new BreadcrumbRequestDto(sessionId, errorTimestamp);

      Timestamp now = new Timestamp(System.currentTimeMillis());
      JsonArray resultData = new JsonArray();
      resultData.add(new JsonObject()
          .put("event_name", "user_login")
          .put("timestamp", "2026-02-27 15:10:00")
          .put("screen_name", "LoginScreen")
          .put("props", "{\"method\":\"google\"}"));

      SubmitQueryResponseDto serviceResponse = SubmitQueryResponseDto.builder()
          .jobId("job-bc-001")
          .status("COMPLETED")
          .message("Breadcrumbs fetched successfully")
          .resultData(resultData)
          .dataScannedInBytes(2048L)
          .createdAt(now)
          .completedAt(now)
          .build();

      when(breadcrumbService.getSessionBreadcrumbs(eq(sessionId), eq(errorTimestamp), eq("test@example.com")))
          .thenReturn(Single.just(serviceResponse));

      CompletionStage<Response<SubmitQueryResponseDto>> result =
          resource.getSessionBreadcrumbs("test@example.com", request);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getJobId()).isEqualTo("job-bc-001");
          assertThat(response.getData().getStatus()).isEqualTo("COMPLETED");
          assertThat(response.getData().getResultData()).isNotNull();
          assertThat(response.getData().getResultData().size()).isEqualTo(1);
          assertThat(response.getData().getMessage()).contains("Breadcrumbs fetched successfully");
          verify(breadcrumbService).getSessionBreadcrumbs(sessionId, errorTimestamp, "test@example.com");
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldPropagateServiceError(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      BreadcrumbRequestDto request = new BreadcrumbRequestDto("session-1", "2026-02-27T15:14:26Z");

      when(breadcrumbService.getSessionBreadcrumbs("session-1", "2026-02-27T15:14:26Z", "test@example.com"))
          .thenReturn(Single.error(new IllegalArgumentException("Session ID is required")));

      CompletionStage<Response<SubmitQueryResponseDto>> result =
          resource.getSessionBreadcrumbs("test@example.com", request);

      result.whenComplete((response, error) -> {
        testContext.verify(() -> {
          assertThat(error).isNotNull();
          assertThat(error).isInstanceOf(IllegalArgumentException.class);
        });
        testContext.completeNow();
      });
    });
  }
}

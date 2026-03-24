package org.dreamhorizon.pulseserver.resources.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.validation.Validator;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.resources.interaction.models.RootCauseRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.interaction.InteractionService;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class InteractionControllerTest {

  @Mock
  private InteractionService interactionService;

  @Mock
  private Validator validator;

  @Mock
  private RootCauseConfig rootCauseConfig;

  @Mock
  private RootCauseService rootCauseService;

  private InteractionController interactionController;

  @BeforeEach
  void setUp() {
    interactionController =
        new InteractionController(interactionService, validator, rootCauseConfig, rootCauseService);
    ProjectContext.setProjectId("test-project");
  }

  @AfterEach
  void tearDown() {
    ProjectContext.clear();
  }

  @Nested
  class GetRootCause {

    @Test
    void shouldCompleteExceptionallyWith400WhenDateQueryParamIsInvalid(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        CompletionStage<Response<RootCauseRestResponse>> result =
            interactionController.getRootCause("my-interaction", "not-a-date");

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(resp);
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            WebApplicationException webException = (WebApplicationException) err;
            assertEquals(400, webException.getResponse().getStatus());
            verifyNoInteractions(rootCauseService);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldCallRootCauseServiceWhenDateIsValidIso(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        LocalDate expectedDate = LocalDate.of(2024, 6, 15);
        RootCauseResult serviceResult = RootCauseResult.builder().mode("flat").build();
        when(rootCauseService.getRootCause("test-project", "my-interaction", expectedDate))
            .thenReturn(Single.just(serviceResult));

        CompletionStage<Response<RootCauseRestResponse>> result =
            interactionController.getRootCause("my-interaction", "2024-06-15");

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp);
            assertNotNull(resp.getData());
            assertEquals("flat", resp.getData().getMode());
            verify(rootCauseService).getRootCause("test-project", "my-interaction", expectedDate);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldDefaultDateToUtcTodayWhenDateParamIsNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        LocalDate expectedToday = LocalDate.now(ZoneOffset.UTC);
        RootCauseResult serviceResult = RootCauseResult.builder().build();
        when(rootCauseService.getRootCause(eq("test-project"), eq("my-interaction"), any(LocalDate.class)))
            .thenReturn(Single.just(serviceResult));

        CompletionStage<Response<RootCauseRestResponse>> result =
            interactionController.getRootCause("my-interaction", null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp);
            ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(rootCauseService).getRootCause(eq("test-project"), eq("my-interaction"), dateCaptor.capture());
            assertEquals(expectedToday, dateCaptor.getValue());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldDefaultDateToUtcTodayWhenDateParamIsBlank(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        LocalDate expectedToday = LocalDate.now(ZoneOffset.UTC);
        RootCauseResult serviceResult = RootCauseResult.builder().build();
        when(rootCauseService.getRootCause(eq("test-project"), eq("my-interaction"), any(LocalDate.class)))
            .thenReturn(Single.just(serviceResult));

        CompletionStage<Response<RootCauseRestResponse>> result =
            interactionController.getRootCause("my-interaction", "   ");

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp);
            ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(rootCauseService).getRootCause(eq("test-project"), eq("my-interaction"), dateCaptor.capture());
            assertEquals(expectedToday, dateCaptor.getValue());
          });
          testContext.completeNow();
        });
      });
    }
  }
}

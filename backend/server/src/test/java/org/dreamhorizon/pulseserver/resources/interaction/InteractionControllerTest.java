package org.dreamhorizon.pulseserver.resources.interaction;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.resources.interaction.models.ErrorAttributionRestResponse;
import org.dreamhorizon.pulseserver.resources.interaction.models.RootCauseRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.interaction.InteractionService;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionService;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
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

  @Mock
  private ErrorAttributionService errorAttributionService;

  private InteractionController interactionController;

  @BeforeEach
  void setUp() {
    interactionController =
        new InteractionController(
            interactionService,
            validator,
            rootCauseConfig,
            rootCauseService,
            errorAttributionService);
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
        ProjectContext.setProjectId("test-project");
        CompletionStage<Response<RootCauseRestResponse>> result =
            interactionController.getRootCause("my-interaction", "not-a-date", null);

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
        ProjectContext.setProjectId("test-project");
        LocalDate expectedDate = LocalDate.of(2024, 6, 15);
        RootCauseResult serviceResult =
            RootCauseResult.builder().mode(RootCauseAnalysisMode.FLAT).build();
        when(rootCauseService.getRootCause(
                eq("test-project"), eq("my-interaction"), eq(expectedDate), any(Instant.class)))
            .thenReturn(Single.just(serviceResult));

        CompletionStage<Response<RootCauseRestResponse>> result =
            interactionController.getRootCause("my-interaction", "2024-06-15", null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp);
            assertNotNull(resp.getData());
            assertEquals(RootCauseAnalysisMode.FLAT, resp.getData().getMode());
            verify(rootCauseService)
                .getRootCause(eq("test-project"), eq("my-interaction"), eq(expectedDate), any(Instant.class));
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldDefaultDateToUtcTodayWhenDateParamIsNull(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("test-project");
        LocalDate expectedToday = LocalDate.now(ZoneOffset.UTC);
        RootCauseResult serviceResult = RootCauseResult.builder().build();
        when(rootCauseService.getRootCause(
                eq("test-project"), eq("my-interaction"), any(LocalDate.class), any(Instant.class)))
            .thenReturn(Single.just(serviceResult));

        CompletionStage<Response<RootCauseRestResponse>> result =
            interactionController.getRootCause("my-interaction", null, null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp);
            ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(rootCauseService)
                .getRootCause(
                    eq("test-project"), eq("my-interaction"), dateCaptor.capture(), any(Instant.class));
            assertEquals(expectedToday, dateCaptor.getValue());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldDefaultDateToUtcTodayWhenDateParamIsBlank(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("test-project");
        LocalDate expectedToday = LocalDate.now(ZoneOffset.UTC);
        RootCauseResult serviceResult = RootCauseResult.builder().build();
        when(rootCauseService.getRootCause(
                eq("test-project"), eq("my-interaction"), any(LocalDate.class), any(Instant.class)))
            .thenReturn(Single.just(serviceResult));

        CompletionStage<Response<RootCauseRestResponse>> result =
            interactionController.getRootCause("my-interaction", "   ", null);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp);
            ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(rootCauseService)
                .getRootCause(
                    eq("test-project"), eq("my-interaction"), dateCaptor.capture(), any(Instant.class));
            assertEquals(expectedToday, dateCaptor.getValue());
          });
          testContext.completeNow();
        });
      });
    }
  }

  @Nested
  class GetErrorAttribution {

    @Test
    void shouldReturn400WhenStartQueryParamIsMissing(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("test-project");
        CompletionStage<Response<ErrorAttributionRestResponse>> result =
            interactionController.getErrorAttribution(
                "my-interaction", "", "2026-01-02T00:00:00Z", false);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(resp);
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            assertEquals(400, ((WebApplicationException) err).getResponse().getStatus());
            verifyNoInteractions(errorAttributionService);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturn400WhenEndIsNotAfterStart(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("test-project");
        CompletionStage<Response<ErrorAttributionRestResponse>> result =
            interactionController.getErrorAttribution(
                "my-interaction",
                "2026-01-03T12:00:00Z",
                "2026-01-03T12:00:00Z",
                false);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(resp);
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            assertEquals(400, ((WebApplicationException) err).getResponse().getStatus());
            verifyNoInteractions(errorAttributionService);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturn200WithInsufficientDataForUnknownInteractionName(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("test-project");
        ErrorAttributionResult body =
            ErrorAttributionResult.builder()
                .trackBInsufficientData(true)
                .nU(0L)
                .nPoorInU(0L)
                .riskRatios(List.of())
                .analysisPhase("1")
                .track("B")
                .diagnosticSpecVersion(ErrorAttributionService.SPEC_VERSION)
                .disclaimer(ErrorAttributionService.DISCLAIMER)
                .build();
        when(errorAttributionService.getErrorAttribution(
                eq("test-project"),
                eq("no-such-span"),
                any(Instant.class),
                any(Instant.class),
                eq(false)))
            .thenReturn(Single.just(body));

        CompletionStage<Response<ErrorAttributionRestResponse>> result =
            interactionController.getErrorAttribution(
                "no-such-span",
                "2026-01-01T00:00:00Z",
                "2026-01-08T00:00:00Z",
                false);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertNotNull(resp);
            assertThat(resp.getData().getTrackBInsufficientData()).isTrue();
            assertThat(resp.getData().getNU()).isZero();
          });
          testContext.completeNow();
        });
      });
    }
  }
}

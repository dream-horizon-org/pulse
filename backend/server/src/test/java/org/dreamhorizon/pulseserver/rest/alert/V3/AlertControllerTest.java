package org.dreamhorizon.pulseserver.rest.alert.V3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.alert.AlertController;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestRequest;
import org.dreamhorizon.pulseserver.resources.alert.models.SnoozeAlertRestResponse;
import org.dreamhorizon.pulseserver.resources.v1.auth.models.AuthHeaders;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GenericSuccessResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
class AlertControllerTest {

  @Mock
  AlertService alertService;

  AlertController alertController;

  final String userEmail = "test@example.com";
  final String authorization = "Bearer token";
  final Integer alertId = 123;

  @BeforeEach
  void setup() {
    alertController = new AlertController(alertService);
  }

  @Nested
  public class TestSnoozeAlert {

    @Test
    void shouldSnoozeAlert(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        long snoozeFromEpoch = Instant.now().plusSeconds(60).getEpochSecond();
        long snoozeUntilEpoch = Instant.now().plusSeconds(3600).getEpochSecond();
        SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(snoozeFromEpoch, snoozeUntilEpoch);
        SnoozeAlertResponse serviceResponse = SnoozeAlertResponse.builder()
            .snoozedFrom(LocalDateTime.ofInstant(Instant.ofEpochSecond(snoozeFromEpoch), ZoneOffset.UTC))
            .snoozedUntil(LocalDateTime.ofInstant(Instant.ofEpochSecond(snoozeUntilEpoch), ZoneOffset.UTC))
            .isSnoozed(false)
            .build();
        AuthHeaders headers = new AuthHeaders(authorization, userEmail);

        ArgumentCaptor<SnoozeAlertRequest> requestCaptor = ArgumentCaptor.forClass(SnoozeAlertRequest.class);
        when(alertService.snoozeAlert(requestCaptor.capture())).thenReturn(Single.just(serviceResponse));

        CompletionStage<Response<SnoozeAlertRestResponse>> result = alertController
            .snoozeAlert(headers, alertId, restRequest).toCompletableFuture();

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals(false, resp.getData().getIsSnoozed());
            assertEquals(snoozeFromEpoch, resp.getData().getSnoozedFrom());
            assertEquals(snoozeUntilEpoch, resp.getData().getSnoozedUntil());
            assertEquals(requestCaptor.getValue().getAlertId(), alertId);
            assertEquals(requestCaptor.getValue().getUpdatedBy(), userEmail);
            assertEquals(requestCaptor.getValue().getSnoozeUntil(),
                LocalDateTime.ofInstant(Instant.ofEpochSecond(snoozeUntilEpoch), ZoneOffset.UTC));
            verify(alertService, times(1)).snoozeAlert(any(SnoozeAlertRequest.class));
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldThrowExceptionIfRuntimeExceptionIsThrownFromService(io.vertx.core.Vertx vertx,
                                                                   VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        long snoozeFromEpoch = Instant.now().plusSeconds(60).getEpochSecond();
        long snoozeUntilEpoch = Instant.now().plusSeconds(3600).getEpochSecond();
        SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(snoozeFromEpoch, snoozeUntilEpoch);
        AuthHeaders headers = new AuthHeaders(authorization, userEmail);

        when(alertService.snoozeAlert(any(SnoozeAlertRequest.class)))
            .thenReturn(Single
                .error(ServiceError.DATABASE_ERROR.getCustomException("Message", "Message", 400)));

        CompletionStage<Response<SnoozeAlertRestResponse>> result = alertController
            .snoozeAlert(headers, alertId, restRequest).toCompletableFuture();

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            WebApplicationException webException = (WebApplicationException) err;
            assertEquals(400, webException.getResponse().getStatus());
            assertEquals("Message", webException.getMessage());
            verify(alertService, times(1)).snoozeAlert(any(SnoozeAlertRequest.class));
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldThrow5xxIfRuntimeExceptionIsThrownFromService(io.vertx.core.Vertx vertx) {
      vertx.runOnContext(v -> {
        long snoozeFromEpoch = Instant.now().plusSeconds(60).getEpochSecond();
        long snoozeUntilEpoch = Instant.now().plusSeconds(3600).getEpochSecond();
        SnoozeAlertRestRequest restRequest = new SnoozeAlertRestRequest(snoozeFromEpoch, snoozeUntilEpoch);
        AuthHeaders headers = new AuthHeaders(authorization, userEmail);

        when(alertService.snoozeAlert(any(SnoozeAlertRequest.class)))
            .thenReturn(Single.error(new RuntimeException("Service failure")));

        Exception exception = assertThrows(Exception.class,
            () -> alertController.snoozeAlert(headers, alertId, restRequest).toCompletableFuture().get());

        assertEquals("Service failure", exception.getCause().getMessage());
      });
    }
  }

  @Nested
  public class TestDeleteSnooze {
    @Test
    void shouldDeleteSnooze(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ArgumentCaptor<DeleteSnoozeRequest> requestCaptor = ArgumentCaptor.forClass(DeleteSnoozeRequest.class);
        AuthHeaders headers = new AuthHeaders(authorization, userEmail);

        when(alertService.deleteSnooze(requestCaptor.capture()))
            .thenReturn(Single.just(EmptyResponse.emptyResponse));

        CompletionStage<Response<GenericSuccessResponse>> result = alertController.deleteSnooze(headers,
            alertId);

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNull(err);
            assertEquals("success", resp.getData().getStatus());
            assertEquals(requestCaptor.getValue().getAlertId(), alertId);
            assertEquals(requestCaptor.getValue().getUpdatedBy(), userEmail);
            verify(alertService, times(1)).deleteSnooze(any(DeleteSnoozeRequest.class));
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldThrowExceptionIfRuntimeExceptionIsThrownFromService(Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        AuthHeaders headers = new AuthHeaders(authorization, userEmail);
        when(alertService.deleteSnooze(any(DeleteSnoozeRequest.class)))
            .thenReturn(Single
                .error(ServiceError.DATABASE_ERROR.getCustomException("Message", "Message", 400)));

        CompletionStage<Response<GenericSuccessResponse>> result = alertController
            .deleteSnooze(headers, alertId).toCompletableFuture();

        result.whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertNotNull(err);
            assertInstanceOf(WebApplicationException.class, err);
            WebApplicationException webException = (WebApplicationException) err;
            assertEquals(400, webException.getResponse().getStatus());
            assertEquals("Message", webException.getMessage());
            verify(alertService, times(1)).deleteSnooze(any(DeleteSnoozeRequest.class));
          });
          testContext.completeNow();
        });
      });
    }
  }
}
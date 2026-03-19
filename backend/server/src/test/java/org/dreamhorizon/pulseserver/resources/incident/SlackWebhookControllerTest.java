package org.dreamhorizon.pulseserver.resources.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.json.Json;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.resources.incident.models.SlackActionPayload;
import org.dreamhorizon.pulseserver.service.incident.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlackWebhookControllerTest {

  private static final long INCIDENT_ID = 42L;
  private static final String USER_ID = "U12345";
  private static final String USER_NAME = "testuser";

  @Mock
  private IncidentService incidentService;

  private SlackWebhookController controller;

  @BeforeEach
  void setUp() {
    controller = new SlackWebhookController(incidentService);
    when(incidentService.acknowledgeIncident(anyLong(), anyString()))
        .thenReturn(Completable.complete());
    when(incidentService.recoverIncident(anyLong(), anyString()))
        .thenReturn(Completable.complete());
    when(incidentService.closeIncident(anyLong(), anyString()))
        .thenReturn(Completable.complete());
  }

  private SlackActionPayload buildPayload(String actionId, String value) {
    return SlackActionPayload.builder()
        .type("block_actions")
        .user(SlackActionPayload.User.builder()
            .id(USER_ID)
            .username(USER_NAME)
            .name("Test User")
            .build())
        .actions(List.of(SlackActionPayload.Action.builder()
            .type("button")
            .actionId(actionId)
            .value(value)
            .build()))
        .build();
  }

  @Test
  void shouldInitializeController() {
    assertThat(controller).isNotNull();
  }

  @Nested
  class HandleInteractive {

    @Test
    void shouldReturnOkForAckAction() throws Exception {
      String rawPayload = Json.encode(buildPayload("ack", String.valueOf(INCIDENT_ID)));

      CompletionStage<Response> result = controller.handleInteractive(rawPayload);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkForRecoverAction() throws Exception {
      String rawPayload = Json.encode(buildPayload("recover", String.valueOf(INCIDENT_ID)));

      CompletionStage<Response> result = controller.handleInteractive(rawPayload);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkForCloseAction() throws Exception {
      String rawPayload = Json.encode(buildPayload("close", String.valueOf(INCIDENT_ID)));

      CompletionStage<Response> result = controller.handleInteractive(rawPayload);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkForUnknownAction() throws Exception {
      String rawPayload = Json.encode(buildPayload("unknown_action", String.valueOf(INCIDENT_ID)));

      CompletionStage<Response> result = controller.handleInteractive(rawPayload);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkWhenActionsListIsEmpty() throws Exception {
      SlackActionPayload payload = SlackActionPayload.builder()
          .type("block_actions")
          .actions(Collections.emptyList())
          .build();
      String rawPayload = Json.encode(payload);

      CompletionStage<Response> result = controller.handleInteractive(rawPayload);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkWhenActionsListIsNull() throws Exception {
      SlackActionPayload payload = SlackActionPayload.builder()
          .type("block_actions")
          .actions(null)
          .build();
      String rawPayload = Json.encode(payload);

      CompletionStage<Response> result = controller.handleInteractive(rawPayload);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkWhenPayloadIsInvalidJson() throws Exception {
      CompletionStage<Response> result = controller.handleInteractive("not-valid-json{{{");

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldCallAcknowledgeOnService() throws Exception {
      String rawPayload = Json.encode(buildPayload("ack", String.valueOf(INCIDENT_ID)));

      controller.handleInteractive(rawPayload);
      // Small delay to allow fire-and-forget subscribe to complete
      Thread.sleep(50);

      verify(incidentService).acknowledgeIncident(eq(INCIDENT_ID), eq("<@" + USER_ID + ">"));
    }

    @Test
    void shouldCallRecoverOnService() throws Exception {
      String rawPayload = Json.encode(buildPayload("recover", String.valueOf(INCIDENT_ID)));

      controller.handleInteractive(rawPayload);
      Thread.sleep(50);

      verify(incidentService).recoverIncident(eq(INCIDENT_ID), eq("<@" + USER_ID + ">"));
    }

    @Test
    void shouldCallCloseOnService() throws Exception {
      String rawPayload = Json.encode(buildPayload("close", String.valueOf(INCIDENT_ID)));

      controller.handleInteractive(rawPayload);
      Thread.sleep(50);

      verify(incidentService).closeIncident(eq(INCIDENT_ID), eq("<@" + USER_ID + ">"));
    }

    @Test
    void shouldNotCallServiceForUnknownAction() throws Exception {
      String rawPayload = Json.encode(buildPayload("unknown", String.valueOf(INCIDENT_ID)));

      controller.handleInteractive(rawPayload);
      Thread.sleep(50);

      verify(incidentService, never()).acknowledgeIncident(anyLong(), anyString());
      verify(incidentService, never()).recoverIncident(anyLong(), anyString());
      verify(incidentService, never()).closeIncident(anyLong(), anyString());
    }

    @Test
    void shouldExtractIncidentIdFromActionValue() throws Exception {
      long specificId = 999L;
      String rawPayload = Json.encode(buildPayload("ack", String.valueOf(specificId)));

      controller.handleInteractive(rawPayload);
      Thread.sleep(50);

      verify(incidentService).acknowledgeIncident(eq(specificId), anyString());
    }

    @Test
    void shouldFormatUserNameWithSlackMention() throws Exception {
      String rawPayload = Json.encode(buildPayload("ack", String.valueOf(INCIDENT_ID)));

      controller.handleInteractive(rawPayload);
      Thread.sleep(50);

      verify(incidentService).acknowledgeIncident(anyLong(), eq("<@" + USER_ID + ">"));
    }

    @Test
    void shouldHandleNullUser() throws Exception {
      SlackActionPayload payload = SlackActionPayload.builder()
          .type("block_actions")
          .user(null)
          .actions(List.of(SlackActionPayload.Action.builder()
              .type("button")
              .actionId("ack")
              .value(String.valueOf(INCIDENT_ID))
              .build()))
          .build();
      String rawPayload = Json.encode(payload);

      controller.handleInteractive(rawPayload);
      Thread.sleep(50);

      verify(incidentService).acknowledgeIncident(eq(INCIDENT_ID), eq("unknown"));
    }

    @Test
    void shouldNotCallServiceWhenActionsEmpty() throws Exception {
      SlackActionPayload payload = SlackActionPayload.builder()
          .type("block_actions")
          .actions(Collections.emptyList())
          .build();
      String rawPayload = Json.encode(payload);

      controller.handleInteractive(rawPayload);
      Thread.sleep(50);

      verify(incidentService, never()).acknowledgeIncident(anyLong(), anyString());
      verify(incidentService, never()).recoverIncident(anyLong(), anyString());
      verify(incidentService, never()).closeIncident(anyLong(), anyString());
    }
  }
}

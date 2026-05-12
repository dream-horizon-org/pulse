package org.dreamhorizon.pulseserver.resources.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest.GrafanaAlert;
import org.dreamhorizon.pulseserver.service.alerts.grafana.GrafanaAlertService;
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
class GrafanaWebhookControllerTest {

  @Mock
  private GrafanaAlertService grafanaAlertService;

  private GrafanaWebhookController controller;

  @BeforeEach
  void setUp() {
    controller = new GrafanaWebhookController(grafanaAlertService);
    when(grafanaAlertService.handleAlert(any())).thenReturn(Completable.complete());
  }

  private GrafanaWebhookRequest buildRequest(String status, String alertName, String summary) {
    GrafanaAlert alert = GrafanaAlert.builder()
        .status(status)
        .labels(Map.of("alertname", alertName, "severity", "warning"))
        .annotations(Map.of("summary", summary))
        .generatorURL("http://grafana.example.com/alerts/1")
        .build();

    return GrafanaWebhookRequest.builder()
        .status(status)
        .receiver("slack-via-oncall-proxy")
        .alerts(List.of(alert))
        .build();
  }

  @Test
  void shouldInitializeController() {
    assertThat(controller).isNotNull();
  }

  @Nested
  class Handle {

    @Test
    void shouldReturnOkForFiringAlert() throws Exception {
      GrafanaWebhookRequest req = buildRequest("firing", "CpuHigh", "CPU > 80%");

      CompletionStage<Response> result = controller.handle(req);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkForResolvedAlert() throws Exception {
      GrafanaWebhookRequest req = buildRequest("resolved", "CpuHigh", "CPU back to normal");

      CompletionStage<Response> result = controller.handle(req);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkForNullRequest() throws Exception {
      CompletionStage<Response> result = controller.handle(null);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkForEmptyAlerts() throws Exception {
      GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
          .status("firing")
          .alerts(List.of())
          .build();

      CompletionStage<Response> result = controller.handle(req);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldReturnOkEvenWhenServiceErrors() throws Exception {
      when(grafanaAlertService.handleAlert(any()))
          .thenReturn(Completable.error(new RuntimeException("boom")));
      GrafanaWebhookRequest req = buildRequest("firing", "X", "y");

      CompletionStage<Response> result = controller.handle(req);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }

    @Test
    void shouldDelegateToService() throws Exception {
      GrafanaWebhookRequest req = buildRequest("firing", "Mem", "OOM");

      controller.handle(req);
      Thread.sleep(50);

      verify(grafanaAlertService).handleAlert(req);
    }

    @Test
    void shouldStillDelegateForNullRequest() throws Exception {
      controller.handle(null);
      Thread.sleep(50);

      verify(grafanaAlertService).handleAlert(null);
    }

    @Test
    void shouldNotThrowWhenServiceImmediatelyThrows() throws Exception {
      when(grafanaAlertService.handleAlert(any()))
          .thenThrow(new RuntimeException("immediate"));
      GrafanaWebhookRequest req = buildRequest("firing", "X", "y");

      CompletionStage<Response> result = controller.handle(req);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
    }
  }

  @Nested
  class Routing {

    @Test
    void shouldUseHandlerForMultipleAlerts() throws Exception {
      GrafanaAlert a1 = GrafanaAlert.builder()
          .status("firing").labels(Map.of("alertname", "A"))
          .annotations(Map.of("summary", "s1")).build();
      GrafanaAlert a2 = GrafanaAlert.builder()
          .status("firing").labels(Map.of("alertname", "B"))
          .annotations(Map.of("summary", "s2")).build();
      GrafanaWebhookRequest req = GrafanaWebhookRequest.builder()
          .status("firing").alerts(List.of(a1, a2)).build();

      controller.handle(req);
      Thread.sleep(50);

      verify(grafanaAlertService).handleAlert(req);
    }

    @Test
    void shouldHandleControllerExceptionsGracefully() throws Exception {
      when(grafanaAlertService.handleAlert(any()))
          .thenThrow(new IllegalStateException("controller path"));
      GrafanaWebhookRequest req = buildRequest("firing", "X", "y");

      CompletionStage<Response> result = controller.handle(req);

      assertThat(result.toCompletableFuture().get().getStatus()).isEqualTo(200);
      verify(grafanaAlertService, never()).handleAlert(null);
    }
  }
}

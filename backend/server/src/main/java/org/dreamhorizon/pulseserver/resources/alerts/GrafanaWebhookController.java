package org.dreamhorizon.pulseserver.resources.alerts;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dto.alerts.grafana.GrafanaWebhookRequest;
import org.dreamhorizon.pulseserver.service.alerts.grafana.GrafanaAlertService;

/**
 * Receives Grafana unified-alerting webhooks. Configure Grafana's webhook contact point to
 * point at {@code POST /v1/alerts/grafana/webhook} on this server.
 *
 * <p><strong>This is a stateless alert pass-through.</strong> It tags the current on-call
 * engineer in a Slack message — nothing more. It does NOT create incidents, persist state,
 * or feed into the incident acknowledgment workflow. For incident lifecycle, see the
 * resources under {@code /v1/incidents/...}.
 *
 * <p>Always returns {@code 200 OK}, even on parse failure, to prevent Grafana from retrying
 * (which would amplify Slack noise). Processing is fire-and-forget so we acknowledge the
 * webhook immediately and do the GoAlert + Slack work asynchronously.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alerts/grafana/webhook")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GrafanaWebhookController {

  private final GrafanaAlertService grafanaAlertService;

  @POST
  public CompletionStage<Response> handle(GrafanaWebhookRequest request) {
    try {
      int alertCount = request != null && request.getAlerts() != null
          ? request.getAlerts().size() : 0;
      log.info("Received Grafana webhook alertCount={} status={}",
          alertCount, request != null ? request.getStatus() : "null");

      grafanaAlertService.handleAlert(request)
          .subscribe(
              () -> log.info("Grafana webhook processed successfully"),
              error -> log.error("Grafana webhook processing failed", error));
    } catch (Exception e) {
      log.error("Failed to handle Grafana webhook", e);
    }

    // Always 200 — Grafana retries on 5xx, which would amplify noise.
    return CompletableFuture.completedFuture(Response.ok().build());
  }
}

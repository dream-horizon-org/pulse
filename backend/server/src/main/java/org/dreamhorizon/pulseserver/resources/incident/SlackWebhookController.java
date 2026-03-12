package org.dreamhorizon.pulseserver.resources.incident;

import com.google.inject.Inject;
import io.vertx.core.json.Json;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.incident.models.SlackActionPayload;
import org.dreamhorizon.pulseserver.service.incident.IncidentService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/incidents/slack/interactive")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public class SlackWebhookController {

  private final IncidentService incidentService;

  @POST
  public CompletionStage<jakarta.ws.rs.core.Response> handleInteractive(
      @FormParam("payload") String rawPayload) {
    try {
      log.info("Received Slack interactive payload");
      SlackActionPayload payload = Json.decodeValue(rawPayload, SlackActionPayload.class);

      if (payload.getActions() == null || payload.getActions().isEmpty()) {
        log.warn("Slack payload has no actions, ignoring");
        return CompletableFuture.completedFuture(jakarta.ws.rs.core.Response.ok().build());
      }

      SlackActionPayload.Action action = payload.getActions().get(0);
      String actionId = action.getActionId();
      long incidentId = Long.parseLong(action.getValue());
      String userName = payload.getUser() != null
          ? "<@" + payload.getUser().getId() + ">"
          : "unknown";

      log.info("Processing Slack action '{}' for incident {} by user {}", actionId, incidentId, userName);

      // Fire-and-forget: subscribe and return 200 immediately (Slack requires < 3s response)
      switch (actionId) {
        case "ack" -> incidentService.acknowledgeIncident(incidentId, userName)
            .subscribe(
                () -> log.info("Acknowledge completed for incident {}", incidentId),
                error -> log.error("Error acknowledging incident {}", incidentId, error));
        case "recover" -> incidentService.recoverIncident(incidentId, userName)
            .subscribe(
                () -> log.info("Recover completed for incident {}", incidentId),
                error -> log.error("Error recovering incident {}", incidentId, error));
        case "close" -> incidentService.closeIncident(incidentId, userName)
            .subscribe(
                () -> log.info("Close completed for incident {}", incidentId),
                error -> log.error("Error closing incident {}", incidentId, error));
        default -> log.warn("Unknown Slack action: {}", actionId);
      }

    } catch (Exception e) {
      log.error("Failed to parse Slack interactive payload", e);
    }

    // Always return 200 OK to Slack
    return CompletableFuture.completedFuture(jakarta.ws.rs.core.Response.ok().build());
  }
}

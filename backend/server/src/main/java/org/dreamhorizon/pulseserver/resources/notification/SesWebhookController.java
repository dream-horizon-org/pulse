package org.dreamhorizon.pulseserver.resources.notification;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.notification.webhook.SesWebhookHandler;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/webhooks/ses")
public class SesWebhookController {

  final SesWebhookHandler sesWebhookHandler;

  @POST
  @Consumes({"application/json", "text/plain"})
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<WebhookResponseDto>> handleSesNotification(String body) {
    log.debug("Received SES webhook notification");

    return sesWebhookHandler
        .handleSnsNotification(body)
        .map(result -> new WebhookResponseDto(result.success(), result.message()))
        .to(RestResponse.jaxrsRestHandler());
  }

  public record WebhookResponseDto(boolean success, String message) {}
}

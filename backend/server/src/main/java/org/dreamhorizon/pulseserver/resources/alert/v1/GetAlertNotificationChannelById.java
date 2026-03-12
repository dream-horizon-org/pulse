package org.dreamhorizon.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertNotificationChannelResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/notificationChannels/{notificationChannelId}")
public class GetAlertNotificationChannelById {
  final AlertService alertsService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertNotificationChannelResponseDto>> getAlertNotificationChannelById(
      @PathParam("notificationChannelId") Integer notificationChannelId) {
    return alertsService
        .getAlertNotificationChannelById(notificationChannelId)
        .switchIfEmpty(Single.error(ServiceError.NOT_FOUND.getCustomException("Notification channel not found")))
        .to(RestResponse.jaxrsRestHandler());
  }
}


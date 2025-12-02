package in.horizonos.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import in.horizonos.pulseserver.dto.request.alerts.CreateAlertNotificationChannelRequestDto;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.alert.core.AlertService;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert/notificationChannels")
public class CreateAlertNotificationChannel {
  final AlertService alertsService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<Boolean>> createAlertNotificationChannel(
      @NotNull CreateAlertNotificationChannelRequestDto createAlertNotificationChannelRequestDto) {
    return alertsService
        .createAlertNotificationChannel(createAlertNotificationChannelRequestDto)
        .to(RestResponse.jaxrsRestHandler());
  }
}

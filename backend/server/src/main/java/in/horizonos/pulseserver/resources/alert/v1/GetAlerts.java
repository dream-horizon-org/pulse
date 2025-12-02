package in.horizonos.pulseserver.resources.alert.v1;

import com.google.inject.Inject;
import in.horizonos.pulseserver.dto.request.alerts.GetAlertsListRequestDto;
import in.horizonos.pulseserver.dto.response.alerts.AlertDetailsPaginatedResponseDto;
import in.horizonos.pulseserver.resources.alert.AlertMapper;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.alert.core.AlertService;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert")
public class GetAlerts {
  final AlertService alertsService;

  private static final AlertMapper mapper = AlertMapper.INSTANCE;

  @GET
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertDetailsPaginatedResponseDto>> getAlerts(
      @BeanParam GetAlertsListRequestDto getAlertsListRequestDto
  ) {
    return alertsService.getAlerts(getAlertsListRequestDto)
        .map(mapper::toAlertDetailsPaginatedResponseDto)
        .to(RestResponse.jaxrsRestHandler());
  }
}

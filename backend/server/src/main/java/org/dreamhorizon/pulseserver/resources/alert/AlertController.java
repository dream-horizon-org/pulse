package org.dreamhorizon.pulseserver.resources.alert;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.CreateAlertRequestDto;
import org.dreamhorizon.pulseserver.resources.alert.models.UpdateAlertRequestDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.alert.core.AlertService;
import org.dreamhorizon.pulseserver.service.alert.core.models.CreateAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.UpdateAlertRequest;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/alert")
public class AlertController {
  private static final AlertMapper mapper = AlertMapper.INSTANCE;
  final AlertService alertsService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertResponseDto>> createAlertV3(
      @NotNull @Valid CreateAlertRequestDto createAlertRequestDto
  ) {
    CreateAlertRequest serviceRequest = mapper.toCreateAlertRequest(createAlertRequestDto);
    return alertsService
        .createAlert(serviceRequest)
        .to(RestResponse.jaxrsRestHandler());
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<AlertResponseDto>> updateAlert(
      @NotNull @Valid UpdateAlertRequestDto updateAlertRequestDto
  ) {
    UpdateAlertRequest serviceRequest = mapper.toUpdateAlertRequest(updateAlertRequestDto);
    return alertsService.updateAlert(serviceRequest)
        .to(RestResponse.jaxrsRestHandler());
  }
}

package org.dreamhorizon.pulseserver.resources.incident;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentRequestDto;
import org.dreamhorizon.pulseserver.resources.incident.models.CreateIncidentResponseDto;
import org.dreamhorizon.pulseserver.resources.incident.models.IncidentResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.incident.IncidentService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/incidents")
public class IncidentController {

  private final IncidentService incidentService;

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<IncidentResponseDto>>> getIncidents() {
    log.info("Received get incidents request");
    return incidentService.getIncidents()
        .to(RestResponse.jaxrsRestHandler());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<CreateIncidentResponseDto>> createIncident(
      @NotNull @Valid CreateIncidentRequestDto request
  ) {
    log.info("Received create incident request: title={}, severity={}, org={}",
        request.getTitle(), request.getSeverity(), request.getOrgIdentifier());
    return incidentService.createIncident(request)
        .to(RestResponse.jaxrsRestHandler());
  }
}

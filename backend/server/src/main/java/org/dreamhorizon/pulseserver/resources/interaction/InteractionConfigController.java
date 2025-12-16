package org.dreamhorizon.pulseserver.resources.interaction;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.interaction.models.AllInteractionDetail;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.interaction.InteractionService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/interaction-configs")
public class InteractionConfigController {
  private static final RestInteractionMapper mapper = RestInteractionMapper.INSTANCE;

  private final InteractionService interactionService;

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<List<AllInteractionDetail>>> getAllInteractions() {
    return interactionService.getAllActiveAndRunningInteractions()
        .map(mapper::toAllInteractionDetail)
        .to(RestResponse.jaxrsRestHandler());
  }
}


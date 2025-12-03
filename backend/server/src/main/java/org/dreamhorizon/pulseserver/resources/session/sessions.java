package org.dreamhorizon.pulseserver.resources.session;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.session.models.GetSessionRequest;
import org.dreamhorizon.pulseserver.resources.session.models.GetSessionResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.session.SessionService;

@Slf4j
@Path("/api/v1")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class sessions {
  private final SessionService sessionService;

  @POST
  @Path("/sessions")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<GetSessionResponse>> getSessions(GetSessionRequest request) {
    return sessionService.getSessions(request)
        .to(RestResponse.jaxrsRestHandler());
  }
}

package in.horizonos.pulseserver.resources.session;

import com.google.inject.Inject;
import in.horizonos.pulseserver.resources.session.models.GetSessionRequest;
import in.horizonos.pulseserver.resources.session.models.GetSessionResponse;
import in.horizonos.pulseserver.rest.io.Response;
import in.horizonos.pulseserver.rest.io.RestResponse;
import in.horizonos.pulseserver.service.session.SessionService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

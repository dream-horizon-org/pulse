package org.dreamhorizon.pulseserver.resources.sessiondetail;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.sessiondetail.models.SessionDetailResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.sessiondetail.SessionDetailService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/session-replay/sessions")
public class SessionDetailController {

  private static final Set<String> VALID_INCLUDES = Set.of("events", "exceptions");

  private final SessionDetailService sessionDetailService;

  @GET
  @Path("/{sessionId}")
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SessionDetailResponse>> getSessionDetail(
      @PathParam("sessionId") String sessionId,
      @QueryParam("include") String include
  ) {
    Set<String> includeSections = parseInclude(include);
    log.info("Fetching session detail for sessionId={}, include={}", sessionId, includeSections);

    return sessionDetailService.getSessionDetail(sessionId, includeSections)
        .to(RestResponse.jaxrsRestHandler());
  }

  private Set<String> parseInclude(String include) {
    if (include == null || include.isBlank()) {
      return Collections.emptySet();
    }
    return Stream.of(include.split(","))
        .map(String::trim)
        .map(String::toLowerCase)
        .filter(VALID_INCLUDES::contains)
        .collect(Collectors.toSet());
  }
}

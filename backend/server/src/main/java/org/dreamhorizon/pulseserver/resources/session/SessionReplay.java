package org.dreamhorizon.pulseserver.resources.session;

import com.google.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.session.models.SnapshotBlobResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SnapshotSourcesResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SnapshotsDataRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.session.SessionReplayService;

@Slf4j
@Path("/v1/sessions")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionReplay {

  private final SessionReplayService sessionReplayService;
  private final SessionReplayMapper sessionReplayMapper;


  @GET
  @Path("/{sessionId}/snapshots-source")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SnapshotSourcesResponse>> getSnapshotsSource(
      @PathParam("sessionId") String sessionId) {
    return sessionReplayService.getBlockSources(sessionId)
        .to(RestResponse.jaxrsRestHandler());
  }

  @GET
  @Path("/{sessionId}/snapshots-data")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<SnapshotBlobResponse>> getSnapshotsData(
      @PathParam("sessionId") String sessionId,
      @Valid @BeanParam SnapshotsDataRequest request) {

    return sessionReplayService.fetchBlockData(
            sessionId,
            request.getStartBlobKey(),
            request.getEndBlobKey())
        .map(sessionReplayMapper::jsonlToSnapshotBlobResponse)
        .to(RestResponse.jaxrsRestHandler());
  }
}

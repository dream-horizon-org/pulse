package org.dreamhorizon.pulseserver.resources.session;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.session.models.FilterConfigResponse;
import org.dreamhorizon.pulseserver.resources.session.models.SessionListingRequest;
import org.dreamhorizon.pulseserver.resources.session.models.SessionListingResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.session.FilterConfigService;
import org.dreamhorizon.pulseserver.service.session.SessionListingService;

import java.util.concurrent.CompletionStage;

@Slf4j
@Path("/v1/sessions")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionListingResource {

    private final SessionListingService sessionListingService;
    private final FilterConfigService filterConfigService;

    @POST
    @Path("/listing")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response<SessionListingResponse>> getSessionListing(SessionListingRequest request) {
        return sessionListingService.getSessionListing(request)
                .to(RestResponse.jaxrsRestHandler());
    }

    @GET
    @Path("/filters")
    @Produces(MediaType.APPLICATION_JSON)
    public CompletionStage<Response<FilterConfigResponse>> getFilters() {
        return Single.just(filterConfigService.getFilterConfig())
                .to(RestResponse.jaxrsRestHandler());
    }
}

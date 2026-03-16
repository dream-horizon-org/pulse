package org.dreamhorizon.pulseserver.resources.eventdefinition;

import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.eventdefinition.models.EventSearchResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.eventdefinition.EventDefinitionService;

/**
 * Serves GET /v1/events -- the existing contract consumed by the Interaction form's
 * AutoCompleteInput component. Returns event definitions in the shape expected by
 * GetScreeNameToEvenQueryMappingResponse on the UI side.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/events")
public class EventSearchController {

  private static final EventDefinitionMapper mapper = EventDefinitionMapper.INSTANCE;

  private final EventDefinitionService eventDefinitionService;

  @GET
  @Consumes(MediaType.WILDCARD)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response<EventSearchResponse>> searchEvents(
      @QueryParam("search_string") @DefaultValue("") String searchString,
      @QueryParam("limit") @DefaultValue("10") int limit
  ) {
    if (searchString.isBlank()) {
      return io.reactivex.rxjava3.core.Single.just(
              EventSearchResponse.builder()
                  .eventList(Collections.emptyList())
                  .recordCount(0)
                  .build())
          .to(RestResponse.jaxrsRestHandler());
    }

    return eventDefinitionService.queryEventDefinitions(searchString, null, limit, 0)
        .map(mapper::toEventSearchResponse)
        .to(RestResponse.jaxrsRestHandler());
  }
}

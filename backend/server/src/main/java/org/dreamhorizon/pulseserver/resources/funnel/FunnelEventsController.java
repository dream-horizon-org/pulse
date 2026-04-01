package org.dreamhorizon.pulseserver.resources.funnel;

import com.google.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelEventsResponse;
import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelFilterValuesResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.eventcatalog.EventCatalogService;

/**
 * Funnel-related read APIs under {@code /v1/funnel} (singular), distinct from CRUD {@code
 * /v1/funnels}.
 */
@Path("/v1/funnel")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelEventsController {

  private final EventCatalogService eventCatalogService;

  /**
   * Custom event names discovered for the project (ClickHouse {@code otel.event_catalog_entries},
   * {@code FilterKey = 'EVENT'}).
   */
  @GET
  @Path("/events")
  public CompletionStage<Response<FunnelEventsResponse>> listEventNames(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required")
          String projectId) {
    return eventCatalogService.listEventNames(projectId).to(RestResponse.jaxrsRestHandler());
  }

  /**
   * Distinct catalog values for {@code filterKey} (ClickHouse {@code otel.event_catalog_entries},
   * {@code FilterValue} where {@code FilterKey} matches the path).
   */
  @GET
  @Path("/filters/{filterKey}/values")
  public CompletionStage<Response<FunnelFilterValuesResponse>> listFilterValues(
      @HeaderParam("X-Project-Id") @NotBlank(message = "X-Project-Id header is required")
          String projectId,
      @PathParam("filterKey") @NotBlank(message = "filterKey path parameter is required")
          String filterKey) {
    return eventCatalogService
        .listFilterValues(projectId, filterKey)
        .to(RestResponse.jaxrsRestHandler());
  }
}

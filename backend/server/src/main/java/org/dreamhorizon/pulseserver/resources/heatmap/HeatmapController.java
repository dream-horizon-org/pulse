package org.dreamhorizon.pulseserver.resources.heatmap;

import com.google.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.filter.RequiresPermission;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapDataRestResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.heatmap.HeatmapService;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/heatmap")
public class HeatmapController {

  private final HeatmapService heatmapService;

  @GET
  @Path("/data")
  @Produces(MediaType.APPLICATION_JSON)
  @RequiresPermission("can_view")
  public CompletionStage<Response<HeatmapDataRestResponse>> getHeatmapData(
      @QueryParam("screenName") String screenName,
      @QueryParam("from") String from,
      @QueryParam("to") String to,
      @QueryParam("app_version") String appVersion,
      @QueryParam("platform") String platform,
      @QueryParam("breakpoint") String breakpoint,
      @QueryParam("geographical_region") String geographicalRegion) {

    return heatmapService
        .getHeatmapData(screenName, from, to, appVersion, platform, breakpoint, geographicalRegion)
        .to(RestResponse.jaxrsRestHandler());
  }
}

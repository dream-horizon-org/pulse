package org.dreamhorizon.pulseserver.service.heatmap;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.heatmap.models.HeatmapDataRestResponse;

public interface HeatmapService {

  Single<HeatmapDataRestResponse> getHeatmapData(
      String screenName,
      String from,
      String to,
      String appVersion,
      String platform,
      String breakpoint,
      String geographicalRegion);
}

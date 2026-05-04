package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.Map;
import org.dreamhorizon.pulseserver.service.rootcause.models.RcaHeatmapFiltersWire;

/** Builds heatmap REST filter payloads from RCA segment dimensions. */
public final class RcaHeatmapFilterWireFactory {

  private RcaHeatmapFilterWireFactory() {}

  public static RcaHeatmapFiltersWire buildFilters(
      Map<String, String> dimensions, String fromIso, String toIso) {
    return RcaHeatmapFiltersWire.builder()
        .breakpoint(null)
        .platform(dimensionOrNull(dimensions, "Platform"))
        .appVersion(dimensionOrNull(dimensions, "AppVersion"))
        .geographicalRegion(dimensionOrNull(dimensions, "GeoState"))
        .fromDate(fromIso)
        .toDate(toIso)
        .build();
  }

  private static String dimensionOrNull(Map<String, String> dimensions, String dimensionKey) {
    if (dimensions == null) {
      return null;
    }
    String v = dimensions.get(dimensionKey);
    if (v == null || v.isBlank()) {
      return null;
    }
    return v;
  }
}

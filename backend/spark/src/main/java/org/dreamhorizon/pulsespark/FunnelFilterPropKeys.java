package org.dreamhorizon.pulsespark;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.dreamhorizon.pulsespark.model.FunnelDefinition;
import org.dreamhorizon.pulsespark.model.FunnelFilter;
import org.dreamhorizon.pulsespark.model.FunnelStep;
import org.dreamhorizon.pulsespark.model.JourneyDefinition;

/**
 * Collects distinct JSON {@code props} keys referenced by funnel/journey filters so they can be
 * uplifted once at Parquet read time.
 */
public final class FunnelFilterPropKeys {

  private FunnelFilterPropKeys() {}

  public static LinkedHashSet<String> collectFromFunnel(FunnelDefinition funnel) {
    var set = new LinkedHashSet<String>();
    collectFromFilters(set, funnel.globalFilters());
    for (FunnelStep step : funnel.steps()) {
      collectFromFilters(set, step.stepFilters());
    }
    return set;
  }

  public static LinkedHashSet<String> collectFromFunnels(List<FunnelDefinition> funnels) {
    var set = new LinkedHashSet<String>();
    for (FunnelDefinition funnel : funnels) {
      collectFromFilters(set, funnel.globalFilters());
      for (FunnelStep step : funnel.steps()) {
        collectFromFilters(set, step.stepFilters());
      }
    }
    return set;
  }

  public static LinkedHashSet<String> collectFromJourney(JourneyDefinition journey) {
    var set = new LinkedHashSet<String>();
    collectFromFilters(set, journey.globalFilters());
    return set;
  }

  public static LinkedHashSet<String> collectFromJourneys(List<JourneyDefinition> journeys) {
    var set = new LinkedHashSet<String>();
    for (JourneyDefinition journey : journeys) {
      collectFromFilters(set, journey.globalFilters());
    }
    return set;
  }

  private static void collectFromFilters(Set<String> out, List<FunnelFilter> filters) {
    for (FunnelFilter f : filters) {
      String fld = f.field();
      if (fld == null || fld.isBlank()) {
        continue;
      }
      if (FilterFieldMapper.isCatalogMapped(fld)) {
        continue;
      }
      String alias = FilterFieldMapper.toParquetColumn(fld);
      if (isReservedColumn(alias)) {
        continue;
      }
      out.add(alias);
    }
  }

  private static boolean isReservedColumn(String name) {
    if (name.equalsIgnoreCase("user_id")) {
      return true;
    }
    for (String c : FunnelComputeJob.READ_COLS) {
      if (c.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }
}

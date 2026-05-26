package org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog;

import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models.EventCatalogEventNameRow;

/**
 * SQL for {@code otel.screen_catalog_entries} — MV-backed discovery catalog for screen names
 * ({@code screen_load} spans in {@code otel.otel_traces}).
 */
public final class ScreenCatalogQueries {

  private ScreenCatalogQueries() {
  }

  /**
   * Distinct screen names for a project.
   *
   * <p>Populated by {@code screen_catalog_entries_mv} from {@code otel.otel_traces}. Uses
   * {@code FINAL} because the table is {@code ReplacingMergeTree}. Alias {@code name} matches
   * {@link EventCatalogEventNameRow}.
   */
  public static String buildListScreenNamesSql(String projectId) {
    String pid = EventCatalogQueries.escapeChStringLiteral(projectId);
    return "SELECT ScreenName AS name FROM otel.screen_catalog_entries FINAL WHERE ProjectId = '"
      + pid
      + "' ORDER BY ScreenName";
  }
}

package org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog;

import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models.EventCatalogEventNameRow;

/**
 * SQL for {@code otel.event_catalog_entries} (Spark EVENTS_INCREMENTAL → ClickHouse).
 */
public final class EventCatalogQueries {

  private EventCatalogQueries() {
  }

  /**
   * Distinct custom event names for a project ({@code FilterKey = 'EVENT'}).
   *
   * <p>Uses {@code FINAL} because the table is {@code ReplacingMergeTree}. Alias {@code name}
   * matches {@link EventCatalogEventNameRow}.
   */
  public static String buildListEventNamesSql(String projectId) {
    String pid = escapeChStringLiteral(projectId);
    return "SELECT FilterValue AS name FROM otel.event_catalog_entries FINAL WHERE ProjectId = '"
      + pid
      + "' AND FilterKey = 'EVENT' ORDER BY FilterValue";
  }

  /**
   * Distinct filter keys for a project, excluding {@code EVENT} (event names are listed
   * separately via {@link #buildListEventNamesSql}).
   */
  public static String buildListFilterKeysSql(String projectId) {
    String pid = escapeChStringLiteral(projectId);
    return "SELECT DISTINCT FilterKey AS filterKey FROM otel.event_catalog_entries FINAL WHERE ProjectId = '"
      + pid
      + "' AND FilterKey != 'EVENT' ORDER BY FilterKey";
  }

  /**
   * Distinct {@code FilterValue} rows for a project and {@code FilterKey} (e.g. {@code OS},
   * {@code COUNTRY}). Alias {@code name} matches {@link
   * EventCatalogEventNameRow}.
   */
  public static String buildListFilterValuesSql(String projectId, String filterKey) {
    String pid = escapeChStringLiteral(projectId);
    String fk = escapeChStringLiteral(filterKey);
    return "SELECT DISTINCT FilterValue AS name FROM otel.event_catalog_entries FINAL WHERE ProjectId = '"
      + pid
      + "' AND FilterKey = '"
      + fk
      + "' ORDER BY FilterValue";
  }

  public static String escapeChStringLiteral(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "''");
  }
}

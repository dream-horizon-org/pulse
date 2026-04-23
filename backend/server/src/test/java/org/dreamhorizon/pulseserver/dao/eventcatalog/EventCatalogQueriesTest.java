package org.dreamhorizon.pulseserver.dao.eventcatalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.EventCatalogQueries;
import org.junit.jupiter.api.Test;

class EventCatalogQueriesTest {

  @Test
  void shouldUseFinalAndEventFilterKey() {
    String sql = EventCatalogQueries.buildListEventNamesSql("proj-1");
    assertThat(sql).contains("FROM otel.event_catalog_entries FINAL");
    assertThat(sql).contains("ProjectId = 'proj-1'");
    assertThat(sql).contains("FilterKey = 'EVENT'");
    assertThat(sql).contains("ORDER BY FilterValue");
  }

  @Test
  void shouldEscapeQuotesInProjectId() {
    assertThat(EventCatalogQueries.escapeChStringLiteral("a'b")).isEqualTo("a''b");
  }

  @Test
  void shouldListFilterKeysExcludingEvent() {
    String sql = EventCatalogQueries.buildListFilterKeysSql("proj-1");
    assertThat(sql).contains("FROM otel.event_catalog_entries FINAL");
    assertThat(sql).contains("ProjectId = 'proj-1'");
    assertThat(sql).contains("FilterKey != 'EVENT'");
    assertThat(sql).contains("DISTINCT FilterKey AS filterKey");
    assertThat(sql).contains("ORDER BY FilterKey");
  }

  @Test
  void shouldListFilterValuesForKey() {
    String sql = EventCatalogQueries.buildListFilterValuesSql("proj-1", "OS");
    assertThat(sql).contains("FROM otel.event_catalog_entries FINAL");
    assertThat(sql).contains("ProjectId = 'proj-1'");
    assertThat(sql).contains("FilterKey = 'OS'");
    assertThat(sql).contains("DISTINCT FilterValue AS name");
    assertThat(sql).contains("ORDER BY FilterValue");
  }

  @Test
  void shouldEscapeFilterKeyInFilterValuesSql() {
    String sql = EventCatalogQueries.buildListFilterValuesSql("p", "O'S");
    assertThat(sql).contains("FilterKey = 'O''S'");
  }
}

package org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScreenCatalogQueriesTest {

  @Test
  void buildListScreenNamesSql_shouldQueryScreenCatalogEntries() {
    String sql = ScreenCatalogQueries.buildListScreenNamesSql("proj-1");
    assertThat(sql)
        .contains("FROM otel.screen_catalog_entries FINAL")
        .contains("ProjectId = 'proj-1'")
        .contains("ORDER BY ScreenName");
  }
}

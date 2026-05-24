package org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventCatalogQueriesTest {

  @Test
  void buildListScreenNamesSql_shouldQueryOtelTracesScreenLoad() {
    String sql = EventCatalogQueries.buildListScreenNamesSql("proj-1");
    assertThat(sql)
        .contains("FROM otel.otel_traces")
        .contains("ProjectId = 'proj-1'")
        .contains("PulseType = 'screen_load'")
        .contains("ScreenName != ''")
        .contains("ORDER BY name LIMIT 500");
  }
}

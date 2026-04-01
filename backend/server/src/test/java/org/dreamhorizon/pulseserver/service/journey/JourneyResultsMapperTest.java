package org.dreamhorizon.pulseserver.service.journey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.dreamhorizon.pulseserver.dao.journeyresults.models.JourneyResultRow;
import org.dreamhorizon.pulseserver.resources.journey.models.JourneyResultsResponse;
import org.junit.jupiter.api.Test;

class JourneyResultsMapperTest {

  @Test
  void shouldMapEntryEdgeAndBuildNodes() {
    List<JourneyResultRow> rows =
        List.of(
            JourneyResultRow.builder()
                .direction("START")
                .posFrom(-1)
                .eventFrom("")
                .posTo(0)
                .eventTo("App_Launch")
                .userCount(100L)
                .build(),
            JourneyResultRow.builder()
                .direction("START")
                .posFrom(0)
                .eventFrom("App_Launch")
                .posTo(1)
                .eventTo("Screen_View: Home")
                .userCount(80L)
                .build());

    JourneyResultsResponse out = JourneyResultsMapper.fromRows(rows);

    assertThat(out.getLinks()).hasSize(2);
    assertThat(out.getLinks().get(0).getSource()).isEqualTo("ENTRY");
    assertThat(out.getLinks().get(0).getTarget()).isEqualTo("App_Launch");
    assertThat(out.getLinks().get(0).getValue()).isEqualTo(100L);
    assertThat(out.getNodes().stream().map(n -> n.getName()))
        .contains("ENTRY", "App_Launch", "Screen_View: Home");
  }
}

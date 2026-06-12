package org.dreamhorizon.pulseserver.service.journey;

import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.models.JourneyResultRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyResultsResponse;
import org.dreamhorizon.pulseserver.service.productAnalysis.journey.JourneyResultsMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JourneyResultsMapperTest {

  @Test
  void shouldHaveNullLastRunAtWhenEmpty() {
    assertThat(JourneyResultsMapper.fromRows(List.of()).getLastRunAt()).isNull();
  }

  @Test
  void shouldSetLastRunAtFromFirstRow() {
    Instant runTime = Instant.parse("2026-05-01T10:00:00Z");
    List<JourneyResultRow> rows = List.of(
      JourneyResultRow.builder()
        .direction("START").posFrom(-1).eventFrom("").posTo(0).eventTo("App_Launch")
        .userCount(100L).runTime(runTime).build());

    assertThat(JourneyResultsMapper.fromRows(rows).getLastRunAt()).isEqualTo(runTime);
  }

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
    assertThat(out.getLinks().get(0).getTarget()).isEqualTo("App_Launch::0");
    assertThat(out.getLinks().get(0).getValue()).isEqualTo(100L);
    assertThat(out.getLinks().get(1).getSource()).isEqualTo("App_Launch::0");
    assertThat(out.getLinks().get(1).getTarget()).isEqualTo("Screen_View: Home::1");
    assertThat(out.getNodes().stream().map(n -> n.getName()))
      .contains("ENTRY", "App_Launch::0", "Screen_View: Home::1");
  }
}

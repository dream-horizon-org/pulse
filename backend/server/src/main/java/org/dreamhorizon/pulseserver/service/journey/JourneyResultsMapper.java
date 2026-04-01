package org.dreamhorizon.pulseserver.service.journey;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.journeyresults.models.JourneyResultRow;
import org.dreamhorizon.pulseserver.resources.journey.models.JourneyResultsResponse;
import org.dreamhorizon.pulseserver.resources.journey.models.JourneySankeyLink;
import org.dreamhorizon.pulseserver.resources.journey.models.JourneySankeyNode;

/** Maps {@code otel.journey_results} rows to Sankey {@code nodes} + {@code links}. */
public final class JourneyResultsMapper {

  private JourneyResultsMapper() {}

  public static JourneyResultsResponse fromRows(List<JourneyResultRow> rows) {
    if (rows == null || rows.isEmpty()) {
      return JourneyResultsResponse.builder()
          .nodes(List.of())
          .links(List.of())
          .build();
    }

    LinkedHashSet<String> nodeNames = new LinkedHashSet<>();
    List<JourneySankeyLink> links = new ArrayList<>(rows.size());

    for (JourneyResultRow r : rows) {
      String src = nodeLabel(r.getPosFrom(), r.getEventFrom());
      String tgt = nodeLabel(r.getPosTo(), r.getEventTo());
      nodeNames.add(src);
      nodeNames.add(tgt);
      long uv = r.getUserCount() == null ? 0L : r.getUserCount();
      links.add(
          JourneySankeyLink.builder().source(src).target(tgt).value(uv).build());
    }

    List<JourneySankeyNode> nodes =
        nodeNames.stream().map(n -> JourneySankeyNode.builder().name(n).build()).toList();

    return JourneyResultsResponse.builder().nodes(nodes).links(links).build();
  }

  /**
   * ENTRY edges use {@code pos_from = -1} and empty {@code event_from}. Other nodes use the event
   * name, or a fallback when empty.
   */
  private static String nodeLabel(Integer pos, String event) {
    int p = pos == null ? Integer.MIN_VALUE : pos;
    boolean emptyEvent = event == null || event.isBlank();
    if (p == -1 && emptyEvent) {
      return "ENTRY";
    }
    if (!emptyEvent) {
      return event.trim();
    }
    return "@" + p;
  }
}

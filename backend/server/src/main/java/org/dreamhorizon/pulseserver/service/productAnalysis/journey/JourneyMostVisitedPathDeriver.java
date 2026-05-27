package org.dreamhorizon.pulseserver.service.productAnalysis.journey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.models.JourneyResultRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyDirection;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyTopPathResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyTopPathStep;

/**
 * Derives a single greedy most-visited path from pre-computed journey transition rows.
 *
 * <p>At each depth level the highest {@code UserCount} edge is chosen. Works for both EVENT and
 * SCREEN journeys because step identity is already normalized in {@code journey_results}.
 *
 * <p>Returned steps are capped to {@link #GRAPH_DEPTH_WINDOW} levels around the anchor so the
 * default UI matches the full-journey graph depth window (positions with {@code abs(pos) <
 * GRAPH_DEPTH_WINDOW}).
 */
public final class JourneyMostVisitedPathDeriver {

  /** Same default depth window as pulse-ui {@code JOURNEY_GRAPH_DEPTH_WINDOW}. */
  static final int GRAPH_DEPTH_WINDOW = 5;

  private JourneyMostVisitedPathDeriver() {
  }

  private record NodeKey(int pos, String event) {
  }

  private record Edge(NodeKey from, NodeKey to, long traffic) {
  }

  public static JourneyTopPathResponse derive(
      List<JourneyResultRow> rows,
      JourneyDirection direction,
      String anchorEvent,
      int depth) {
    if (rows == null || rows.isEmpty() || anchorEvent == null || anchorEvent.isBlank()) {
      return empty("No journey results available");
    }

    String anchor = anchorEvent.trim();
    long anchorTraffic = findEntryTraffic(rows, anchor);
    List<Edge> edges = parseEdges(rows);
    if (edges.isEmpty()) {
      return empty("No transition edges available");
    }

    JourneyDirection dir = direction == null ? JourneyDirection.START : direction;
    if (dir == JourneyDirection.END) {
      return capForGraphWindow(deriveEnd(edges, anchor, depth, anchorTraffic));
    }
    return capForGraphWindow(deriveStart(edges, anchor, depth, anchorTraffic));
  }

  private static JourneyTopPathResponse capForGraphWindow(JourneyTopPathResponse raw) {
    if (raw.getSteps() == null || raw.getSteps().size() <= 1) {
      return raw;
    }
    List<JourneyTopPathStep> capped =
        raw.getSteps().stream()
            .filter(s -> Math.abs(s.getPosition()) < GRAPH_DEPTH_WINDOW)
            .toList();
    if (capped.size() == raw.getSteps().size()) {
      return raw;
    }
    if (capped.size() <= 1) {
      return JourneyTopPathResponse.builder()
          .steps(capped)
          .complete(false)
          .anchorTraffic(raw.getAnchorTraffic())
          .pathTraffic(capped.isEmpty() ? 0L : capped.get(capped.size() - 1).getTraffic())
          .incompletenessReason("Not enough path data within the journey graph depth window")
          .build();
    }
    long pathTraffic =
        capped.stream().skip(1).mapToLong(JourneyTopPathStep::getTraffic).min().orElse(0L);
    return JourneyTopPathResponse.builder()
        .steps(capped)
        .complete(true)
        .anchorTraffic(displayAnchorTraffic(capped, raw.getAnchorTraffic()))
        .pathTraffic(pathTraffic)
        .incompletenessReason(null)
        .build();
  }

  private static long displayAnchorTraffic(
      List<JourneyTopPathStep> steps, long fallbackEntryTraffic) {
    if (steps.isEmpty()) {
      return fallbackEntryTraffic;
    }
    return steps.stream()
        .filter(s -> s.getPosition() == 0)
        .mapToLong(JourneyTopPathStep::getTraffic)
        .findFirst()
        .orElse(fallbackEntryTraffic);
  }

  private static JourneyTopPathResponse deriveEnd(
      List<Edge> edges, String anchor, int depth, long anchorTraffic) {
    NodeKey current = new NodeKey(0, anchor);
    List<JourneyTopPathStep> steps = new ArrayList<>();
    long pathTraffic = Long.MAX_VALUE;

    long anchorStepTraffic = maxIncomingTraffic(edges, current);
    if (anchorStepTraffic <= 0 && anchorTraffic > 0) {
      anchorStepTraffic = anchorTraffic;
    }
    steps.add(toStep(current, anchorStepTraffic));

    boolean gap = false;
    while (true) {
      Edge best = bestIncoming(edges, current);
      if (best == null) {
        gap = steps.size() == 1;
        break;
      }
      if (isEntryNode(best.from())) {
        break;
      }
      pathTraffic = Math.min(pathTraffic, best.traffic());
      current = best.from();
      steps.add(0, toStep(current, best.traffic()));
      if (depth > 0 && current.pos() <= -depth) {
        break;
      }
    }

    return buildResult(steps, anchorTraffic, pathTraffic, gap);
  }

  private static JourneyTopPathResponse deriveStart(
      List<Edge> edges, String anchor, int depth, long anchorTraffic) {
    NodeKey current = new NodeKey(0, anchor);
    List<JourneyTopPathStep> steps = new ArrayList<>();
    long pathTraffic = Long.MAX_VALUE;

    long anchorStepTraffic = sumOutgoingTraffic(edges, current);
    if (anchorStepTraffic <= 0 && anchorTraffic > 0) {
      anchorStepTraffic = anchorTraffic;
    } else if (anchorStepTraffic <= 0) {
      anchorStepTraffic = maxOutgoingTraffic(edges, current);
    }
    steps.add(toStep(current, anchorStepTraffic));

    boolean gap = false;
    while (true) {
      Edge best = bestOutgoing(edges, current);
      if (best == null) {
        gap = steps.size() == 1;
        break;
      }
      pathTraffic = Math.min(pathTraffic, best.traffic());
      current = best.to();
      steps.add(toStep(current, best.traffic()));
      if (depth > 0 && current.pos() >= depth) {
        break;
      }
    }

    return buildResult(steps, anchorTraffic, pathTraffic, gap);
  }

  private static JourneyTopPathResponse buildResult(
      List<JourneyTopPathStep> steps,
      long anchorTraffic,
      long pathTraffic,
      boolean gap) {
    boolean complete = !gap && steps.size() > 1;
    String reason = complete ? null : "Not enough path data to derive a full top path";
    return JourneyTopPathResponse.builder()
        .steps(List.copyOf(steps))
        .complete(complete)
        .anchorTraffic(displayAnchorTraffic(steps, anchorTraffic))
        .pathTraffic(pathTraffic == Long.MAX_VALUE ? 0L : pathTraffic)
        .incompletenessReason(reason)
        .build();
  }

  private static JourneyTopPathResponse empty(String reason) {
    return JourneyTopPathResponse.builder()
        .steps(List.of())
        .complete(false)
        .anchorTraffic(0L)
        .pathTraffic(0L)
        .incompletenessReason(reason)
        .build();
  }

  private static List<Edge> parseEdges(List<JourneyResultRow> rows) {
    List<Edge> edges = new ArrayList<>();
    for (JourneyResultRow row : rows) {
      if (row.getPosFrom() == null || row.getPosTo() == null) {
        continue;
      }
      int posFrom = row.getPosFrom();
      int posTo = row.getPosTo();
      if (posTo != posFrom + 1) {
        continue;
      }
      String eventFrom = normalizeEvent(row.getEventFrom());
      String eventTo = normalizeEvent(row.getEventTo());
      if (eventTo.isEmpty()) {
        continue;
      }
      long traffic = row.getUserCount() == null ? 0L : row.getUserCount();
      edges.add(new Edge(new NodeKey(posFrom, eventFrom), new NodeKey(posTo, eventTo), traffic));
    }
    return edges;
  }

  private static long findEntryTraffic(List<JourneyResultRow> rows, String anchor) {
    for (JourneyResultRow row : rows) {
      if (row.getPosFrom() == null || row.getPosTo() == null) {
        continue;
      }
      if (row.getPosFrom() == -1
          && row.getPosTo() == 0
          && isBlankEvent(row.getEventFrom())
          && anchor.equals(normalizeEvent(row.getEventTo()))) {
        return row.getUserCount() == null ? 0L : row.getUserCount();
      }
    }
    return 0L;
  }

  private static Edge bestIncoming(List<Edge> edges, NodeKey target) {
    return edges.stream()
        .filter(e -> e.to().equals(target) && !isEntryNode(e.from()))
        .max(edgeComparator())
        .orElse(null);
  }

  private static Edge bestOutgoing(List<Edge> edges, NodeKey source) {
    return edges.stream()
        .filter(e -> e.from().equals(source))
        .max(edgeComparator())
        .orElse(null);
  }

  private static Comparator<Edge> edgeComparator() {
    return Comparator.comparingLong(Edge::traffic)
        .thenComparing(e -> e.from().event())
        .thenComparing(e -> e.to().event());
  }

  private static long maxIncomingTraffic(List<Edge> edges, NodeKey target) {
    return edges.stream()
        .filter(e -> e.to().equals(target) && !isEntryNode(e.from()))
        .mapToLong(Edge::traffic)
        .max()
        .orElse(0L);
  }

  private static long maxOutgoingTraffic(List<Edge> edges, NodeKey source) {
    return edges.stream()
        .filter(e -> e.from().equals(source))
        .mapToLong(Edge::traffic)
        .max()
        .orElse(0L);
  }

  private static long sumOutgoingTraffic(List<Edge> edges, NodeKey source) {
    return edges.stream()
        .filter(e -> e.from().equals(source))
        .mapToLong(Edge::traffic)
        .sum();
  }

  private static boolean isEntryNode(NodeKey node) {
    return node.pos() == -1 && node.event().isEmpty();
  }

  private static boolean isBlankEvent(String event) {
    return event == null || event.isBlank();
  }

  private static String normalizeEvent(String event) {
    return event == null ? "" : event.trim();
  }

  private static JourneyTopPathStep toStep(NodeKey node, long traffic) {
    return JourneyTopPathStep.builder()
        .position(node.pos())
        .stepName(node.event())
        .traffic(traffic)
        .build();
  }
}

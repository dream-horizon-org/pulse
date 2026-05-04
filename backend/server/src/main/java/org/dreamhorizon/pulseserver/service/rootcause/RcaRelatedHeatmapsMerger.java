package org.dreamhorizon.pulseserver.service.rootcause;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

/**
 * Injects {@code related_heatmaps} ({@code screens} + {@code heatmap_filters}) into each
 * {@code report.structured.segments[i]} using RCA segment dimensions and the RCA query window.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public final class RcaRelatedHeatmapsMerger {

  private final ObjectMapper objectMapper;

  /**
   * Merges related heatmap metadata for each segment index {@code i} where both the LLM segments
   * array and {@code rcaSegments} have an element at {@code i}.
   *
   * <p>{@code to_date} is the UTC calendar date of the last instant inside the RCA window (exclusive
   * end minus one nanosecond), matching span timestamps {@code >= startInclusive} and {@code <
   * endExclusive}.
   */
  public void mergeInto(
      ObjectNode responseRoot,
      List<RootCauseSegment> rcaSegments,
      RootCauseQueryBuilder.Window window,
      List<String> screens) {
    if (rcaSegments == null || rcaSegments.isEmpty()) {
      return;
    }
    List<String> screenList = screens == null ? List.of() : screens;
    JsonNode structured = responseRoot.path("report").path("structured");
    if (!structured.isObject()) {
      log.debug("RCA related heatmaps: report.structured missing or not an object");
      return;
    }
    JsonNode segmentsNode = structured.path("segments");
    if (!segmentsNode.isArray()) {
      log.debug("RCA related heatmaps: report.structured.segments missing or not an array");
      return;
    }
    LocalDate fromDate = LocalDate.ofInstant(window.startInclusive, ZoneOffset.UTC);
    LocalDate toDate =
        LocalDate.ofInstant(window.endExclusive.minusNanos(1), ZoneOffset.UTC);
    String fromIso = fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    String toIso = toDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    int n = Math.min(segmentsNode.size(), rcaSegments.size());
    for (int i = 0; i < n; i++) {
      JsonNode seg = segmentsNode.get(i);
      if (!seg.isObject()) {
        continue;
      }
      ObjectNode segObj = (ObjectNode) seg;
      ObjectNode related = objectMapper.createObjectNode();
      ArrayNode screensArray = objectMapper.createArrayNode();
      for (String s : screenList) {
        screensArray.add(s);
      }
      related.set("screens", screensArray);
      related.set(
          "heatmap_filters",
          objectMapper.valueToTree(
              RcaHeatmapFilterWireFactory.buildFilters(rcaSegments.get(i).getDimensions(), fromIso, toIso)));
      segObj.set("related_heatmaps", related);
    }
  }
}

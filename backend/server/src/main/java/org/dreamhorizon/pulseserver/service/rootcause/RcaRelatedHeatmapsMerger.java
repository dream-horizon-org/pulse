package org.dreamhorizon.pulseserver.service.rootcause;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

/**
 * Injects {@code related_heatmaps} (placeholder screen + {@code heatmap_filters}) into each
 * {@code report.structured.segments[i]} using RCA segment dimensions and the RCA query window.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public final class RcaRelatedHeatmapsMerger {

  private static final String PLACEHOLDER_SCREEN = "test_screen";

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
      ObjectNode responseRoot, List<RootCauseSegment> rcaSegments, RootCauseQueryBuilder.Window window) {
    if (rcaSegments == null || rcaSegments.isEmpty()) {
      return;
    }
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
      ArrayNode screens = objectMapper.createArrayNode();
      screens.add(PLACEHOLDER_SCREEN);
      related.set("screens", screens);
      related.set(
          "heatmap_filters",
          buildHeatmapFilters(rcaSegments.get(i).getDimensions(), fromIso, toIso));
      segObj.set("related_heatmaps", related);
    }
  }

  private ObjectNode buildHeatmapFilters(Map<String, String> dimensions, String fromIso, String toIso) {
    ObjectNode filters = objectMapper.createObjectNode();
    filters.putNull("breakpoint");
    putDimensionAsHeatmapField(filters, "platform", dimensions, "Platform");
    putDimensionAsHeatmapField(filters, "app_version", dimensions, "AppVersion");
    putDimensionAsHeatmapField(filters, "geographical_region", dimensions, "GeoState");
    filters.put("from_date", fromIso);
    filters.put("to_date", toIso);
    return filters;
  }

  private static void putDimensionAsHeatmapField(
      ObjectNode filters, String jsonKey, Map<String, String> dimensions, String dimKey) {
    if (dimensions == null) {
      filters.putNull(jsonKey);
      return;
    }
    String v = dimensions.get(dimKey);
    if (v == null || v.isBlank()) {
      filters.putNull(jsonKey);
    } else {
      filters.put(jsonKey, v);
    }
  }
}

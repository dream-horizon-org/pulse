package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RcaRelatedHeatmapsMergerTest {

  private static final LocalDate ANCHOR = LocalDate.of(2025, 3, 10);
  private static final Instant WINDOW_END = Instant.parse("2025-03-10T15:30:00Z");

  private ObjectMapper objectMapper;
  private RcaRelatedHeatmapsMerger merger;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    merger = new RcaRelatedHeatmapsMerger(objectMapper);
  }

  private RootCauseQueryBuilder.Window window7Day() {
    return new RootCauseQueryBuilder.Window(ANCHOR, 7, WINDOW_END);
  }

  @Nested
  class MergeInto {

    @Test
    void shouldAddRelatedHeatmapsPerSegmentWithFiltersAndScreens() throws Exception {
      ObjectNode root =
          (ObjectNode)
              objectMapper.readTree(
                  """
                  {
                    "report": {
                      "structured": {
                        "segments": [
                          { "rank": 1, "title": "A" },
                          { "rank": 2, "title": "B" }
                        ]
                      }
                    }
                  }
                  """);
      List<RootCauseSegment> rcaSegments =
          List.of(
              RootCauseSegment.builder()
                  .dimensions(
                      Map.of(
                          "Platform", "Android",
                          "AppVersion", "9.6.1",
                          "GeoState", "CA"))
                  .build(),
              RootCauseSegment.builder()
                  .dimensions(Map.of("Platform", "Android", "AppVersion", "9.6.1"))
                  .build());

      merger.mergeInto(root, rcaSegments, window7Day(), List.of("home", "checkout"));

      ObjectNode seg0 = (ObjectNode) root.path("report").path("structured").path("segments").get(0);
      assertThat(seg0.path("related_heatmaps").path("screens").get(0).asText()).isEqualTo("home");
      assertThat(seg0.path("related_heatmaps").path("screens").get(1).asText()).isEqualTo("checkout");
      ObjectNode hf0 = (ObjectNode) seg0.path("related_heatmaps").path("heatmap_filters");
      assertThat(hf0.path("platform").asText()).isEqualTo("Android");
      assertThat(hf0.path("app_version").asText()).isEqualTo("9.6.1");
      assertThat(hf0.path("geographical_region").asText()).isEqualTo("CA");
      assertThat(hf0.path("breakpoint").isNull()).isTrue();
      assertThat(hf0.path("from_date").asText()).isEqualTo("2025-03-04");
      assertThat(hf0.path("to_date").asText()).isEqualTo("2025-03-10");

      ObjectNode seg1 = (ObjectNode) root.path("report").path("structured").path("segments").get(1);
      ObjectNode hf1 = (ObjectNode) seg1.path("related_heatmaps").path("heatmap_filters");
      assertThat(hf1.path("platform").asText()).isEqualTo("Android");
      assertThat(hf1.path("geographical_region").isNull()).isTrue();
    }

    @Test
    void shouldNoOpWhenRcaSegmentsEmpty() throws Exception {
      String json = "{\"report\":{\"structured\":{\"segments\":[{\"x\":1}]}}}";
      ObjectNode root = (ObjectNode) objectMapper.readTree(json);
      merger.mergeInto(root, List.of(), window7Day(), List.of("ignored"));
      assertThat(root.path("report").path("structured").path("segments").get(0).path("related_heatmaps").isMissingNode())
          .isTrue();
    }

    @Test
    void shouldNoOpWhenStructuredMissing() {
      ObjectNode root = objectMapper.createObjectNode();
      merger.mergeInto(
          root,
          List.of(RootCauseSegment.builder().dimensions(Map.of("Platform", "iOS")).build()),
          window7Day(),
          List.of());
      assertThat(root.isEmpty()).isTrue();
    }

    @Test
    void shouldMergeOnlyMinOfArrayAndRcaSegmentSizes() throws Exception {
      ObjectNode root =
          (ObjectNode)
              objectMapper.readTree(
                  "{\"report\":{\"structured\":{\"segments\":[{\"rank\":1},{\"rank\":2},{\"rank\":3}]}}}");
      List<RootCauseSegment> rcaSegments =
          List.of(
              RootCauseSegment.builder().dimensions(Map.of("Platform", "A")).build(),
              RootCauseSegment.builder().dimensions(Map.of("Platform", "B")).build());

      merger.mergeInto(root, rcaSegments, window7Day(), List.of("s1", "s2"));

      JsonNode segments = root.path("report").path("structured").path("segments");
      assertThat(segments.get(0).path("related_heatmaps").path("heatmap_filters").path("platform").asText())
          .isEqualTo("A");
      assertThat(segments.get(1).path("related_heatmaps").path("heatmap_filters").path("platform").asText())
          .isEqualTo("B");
      assertThat(segments.get(2).path("related_heatmaps").isMissingNode()).isTrue();
    }

    @Test
    void shouldEmitEmptyScreensArrayWhenListEmpty() throws Exception {
      ObjectNode root =
          (ObjectNode)
              objectMapper.readTree(
                  "{\"report\":{\"structured\":{\"segments\":[{\"rank\":1}]}}}");
      merger.mergeInto(
          root,
          List.of(RootCauseSegment.builder().dimensions(Map.of("Platform", "Android")).build()),
          window7Day(),
          List.of());
      assertThat(root.path("report").path("structured").path("segments").get(0).path("related_heatmaps").path("screens").size())
          .isEqualTo(0);
    }
  }
}

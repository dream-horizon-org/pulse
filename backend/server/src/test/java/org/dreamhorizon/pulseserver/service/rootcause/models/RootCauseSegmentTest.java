package org.dreamhorizon.pulseserver.service.rootcause.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RootCauseSegmentTest {

  @Nested
  class BuilderAndConstruction {

    @Test
    void shouldBuildSegmentWithAllFields() {
      Map<String, String> dimensions = Map.of("Platform", "Android", "AppVersion", "1.0.0");
      Map<String, Object> metrics =
          Map.of("volume", 100, "apdex", 0.5, "error_rate", 5.0);
      Map<String, Double> deltas = Map.of("error_rate", 15.5, "apdex", -0.2);
      List<String> exampleSessions = List.of("session-1", "session-2");

      RootCauseSegment segment =
          RootCauseSegment.builder()
              .label("Android + App 1.0.0")
              .dimensions(dimensions)
              .metrics(metrics)
              .deltas(deltas)
              .exampleSessionIds(exampleSessions)
              .build();

      assertThat(segment.getLabel()).isEqualTo("Android + App 1.0.0");
      assertThat(segment.getDimensions()).isEqualTo(dimensions);
      assertThat(segment.getMetrics()).isEqualTo(metrics);
      assertThat(segment.getDeltas()).isEqualTo(deltas);
      assertThat(segment.getExampleSessionIds()).isEqualTo(exampleSessions);
    }

    @Test
    void shouldBuildSegmentWithMinimalFields() {
      RootCauseSegment segment = RootCauseSegment.builder().build();

      assertThat(segment.getLabel()).isNull();
      assertThat(segment.getDimensions()).isNull();
      assertThat(segment.getMetrics()).isNull();
      assertThat(segment.getDeltas()).isNull();
      assertThat(segment.getExampleSessionIds()).isNotNull().isEmpty();
    }

    @Test
    void shouldInitializeExampleSessionIdsToEmptyListByDefault() {
      RootCauseSegment segment = RootCauseSegment.builder().label("Test").build();

      assertThat(segment.getExampleSessionIds()).isNotNull().isEmpty();
    }

    @Test
    void shouldAllowNullDimensions() {
      RootCauseSegment segment =
          RootCauseSegment.builder().label("Test").dimensions(null).build();

      assertThat(segment.getDimensions()).isNull();
    }

    @Test
    void shouldAllowNullMetrics() {
      RootCauseSegment segment = RootCauseSegment.builder().label("Test").metrics(null).build();

      assertThat(segment.getMetrics()).isNull();
    }

    @Test
    void shouldAllowNullDeltas() {
      RootCauseSegment segment = RootCauseSegment.builder().label("Test").deltas(null).build();

      assertThat(segment.getDeltas()).isNull();
    }
  }

  @Nested
  class DimensionsHandling {

    @Test
    void shouldStoreSingleDimension() {
      Map<String, String> dimensions = Map.of("Platform", "iOS");

      RootCauseSegment segment =
          RootCauseSegment.builder().dimensions(dimensions).build();

      assertThat(segment.getDimensions()).hasSize(1).containsEntry("Platform", "iOS");
    }

    @Test
    void shouldStoreMultipleDimensions() {
      Map<String, String> dimensions = new HashMap<>();
      dimensions.put("Platform", "Android");
      dimensions.put("AppVersion", "2.0.0");
      dimensions.put("GeoState", "CA");
      dimensions.put("DeviceModel", "Pixel 5");

      RootCauseSegment segment =
          RootCauseSegment.builder().dimensions(dimensions).build();

      assertThat(segment.getDimensions())
          .hasSize(4)
          .containsEntry("Platform", "Android")
          .containsEntry("AppVersion", "2.0.0")
          .containsEntry("GeoState", "CA")
          .containsEntry("DeviceModel", "Pixel 5");
    }

    @Test
    void shouldHandleEmptyDimensions() {
      Map<String, String> emptyDimensions = new HashMap<>();

      RootCauseSegment segment =
          RootCauseSegment.builder().dimensions(emptyDimensions).build();

      assertThat(segment.getDimensions()).isEmpty();
    }
  }

  @Nested
  class MetricsHandling {

    @Test
    void shouldStoreNumericMetrics() {
      Map<String, Object> metrics = new HashMap<>();
      metrics.put("volume", 1000);
      metrics.put("apdex", 0.75);
      metrics.put("error_rate", 2.5);

      RootCauseSegment segment = RootCauseSegment.builder().metrics(metrics).build();

      assertThat(segment.getMetrics())
          .hasSize(3)
          .containsEntry("volume", 1000)
          .containsEntry("apdex", 0.75)
          .containsEntry("error_rate", 2.5);
    }

    @Test
    void shouldStoreMixedTypeMetrics() {
      Map<String, Object> metrics = new HashMap<>();
      metrics.put("count", 100);
      metrics.put("ratio", 0.5);
      metrics.put("label", "PERFORMANCE_ISSUE");

      RootCauseSegment segment = RootCauseSegment.builder().metrics(metrics).build();

      assertThat(segment.getMetrics()).hasSize(3).containsEntry("label", "PERFORMANCE_ISSUE");
    }

    @Test
    void shouldHandleEmptyMetrics() {
      Map<String, Object> emptyMetrics = new HashMap<>();

      RootCauseSegment segment = RootCauseSegment.builder().metrics(emptyMetrics).build();

      assertThat(segment.getMetrics()).isEmpty();
    }
  }

  @Nested
  class DeltasHandling {

    @Test
    void shouldStoreDeltaValues() {
      Map<String, Double> deltas = new HashMap<>();
      deltas.put("error_rate", 15.5);
      deltas.put("apdex", -0.25);

      RootCauseSegment segment = RootCauseSegment.builder().deltas(deltas).build();

      assertThat(segment.getDeltas())
          .hasSize(2)
          .containsEntry("error_rate", 15.5)
          .containsEntry("apdex", -0.25);
    }

    @Test
    void shouldStorePositiveAndNegativeDeltas() {
      Map<String, Double> deltas = new HashMap<>();
      deltas.put("positive_metric", 20.0);
      deltas.put("negative_metric", -5.5);
      deltas.put("zero_metric", 0.0);

      RootCauseSegment segment = RootCauseSegment.builder().deltas(deltas).build();

      assertThat(segment.getDeltas())
          .hasSize(3)
          .containsEntry("positive_metric", 20.0)
          .containsEntry("negative_metric", -5.5)
          .containsEntry("zero_metric", 0.0);
    }

    @Test
    void shouldHandleEmptyDeltas() {
      Map<String, Double> emptyDeltas = new HashMap<>();

      RootCauseSegment segment = RootCauseSegment.builder().deltas(emptyDeltas).build();

      assertThat(segment.getDeltas()).isEmpty();
    }
  }

  @Nested
  class ExampleSessionIdsHandling {

    @Test
    void shouldStoreSingleExampleSession() {
      List<String> sessions = List.of("session-123");

      RootCauseSegment segment =
          RootCauseSegment.builder().exampleSessionIds(sessions).build();

      assertThat(segment.getExampleSessionIds()).hasSize(1).contains("session-123");
    }

    @Test
    void shouldStoreMultipleExampleSessions() {
      List<String> sessions = List.of("session-1", "session-2", "session-3");

      RootCauseSegment segment =
          RootCauseSegment.builder().exampleSessionIds(sessions).build();

      assertThat(segment.getExampleSessionIds())
          .hasSize(3)
          .contains("session-1", "session-2", "session-3");
    }

    @Test
    void shouldHandleEmptySessionsList() {
      List<String> emptySessions = new ArrayList<>();

      RootCauseSegment segment =
          RootCauseSegment.builder().exampleSessionIds(emptySessions).build();

      assertThat(segment.getExampleSessionIds()).isEmpty();
    }

    @Test
    void shouldDefaultToEmptyListIfNotSet() {
      RootCauseSegment segment = RootCauseSegment.builder().build();

      assertThat(segment.getExampleSessionIds()).isNotNull().isEmpty();
    }
  }

  @Nested
  class LabelHandling {

    @Test
    void shouldStoreLabelAsHierarchicalFormat() {
      String hierarchicalLabel = "Android + App 3.4.5 + Jio";

      RootCauseSegment segment =
          RootCauseSegment.builder().label(hierarchicalLabel).build();

      assertThat(segment.getLabel()).isEqualTo(hierarchicalLabel);
    }

    @Test
    void shouldStoreLabelAsFlatFormat() {
      String flatLabel = "Platform: Android";

      RootCauseSegment segment = RootCauseSegment.builder().label(flatLabel).build();

      assertThat(segment.getLabel()).isEqualTo(flatLabel);
    }

    @Test
    void shouldAllowNullLabel() {
      RootCauseSegment segment = RootCauseSegment.builder().build();

      assertThat(segment.getLabel()).isNull();
    }
  }

  @Nested
  class DataModification {

    @Test
    void shouldSupportSetterForDimensions() {
      RootCauseSegment segment = new RootCauseSegment();
      Map<String, String> dimensions = Map.of("Platform", "iOS");

      segment.setDimensions(dimensions);

      assertThat(segment.getDimensions()).isEqualTo(dimensions);
    }

    @Test
    void shouldSupportSetterForMetrics() {
      RootCauseSegment segment = new RootCauseSegment();
      Map<String, Object> metrics = Map.of("error_rate", 5.0);

      segment.setMetrics(metrics);

      assertThat(segment.getMetrics()).isEqualTo(metrics);
    }

    @Test
    void shouldSupportSetterForDeltas() {
      RootCauseSegment segment = new RootCauseSegment();
      Map<String, Double> deltas = Map.of("error_rate", 15.5);

      segment.setDeltas(deltas);

      assertThat(segment.getDeltas()).isEqualTo(deltas);
    }

    @Test
    void shouldSupportSetterForExampleSessionIds() {
      RootCauseSegment segment = new RootCauseSegment();
      List<String> sessions = List.of("session-1", "session-2");

      segment.setExampleSessionIds(sessions);

      assertThat(segment.getExampleSessionIds()).isEqualTo(sessions);
    }

    @Test
    void shouldSupportSetterForLabel() {
      RootCauseSegment segment = new RootCauseSegment();
      String label = "Android + App 1.0.0";

      segment.setLabel(label);

      assertThat(segment.getLabel()).isEqualTo(label);
    }
  }

  @Nested
  class NoArgsConstructor {

    @Test
    void shouldAllowCreationViaNoArgsConstructor() {
      RootCauseSegment segment = new RootCauseSegment();

      assertThat(segment).isNotNull();
      assertThat(segment.getLabel()).isNull();
      assertThat(segment.getDimensions()).isNull();
      assertThat(segment.getMetrics()).isNull();
      assertThat(segment.getDeltas()).isNull();
      assertThat(segment.getExampleSessionIds()).isNotNull().isEmpty();
    }
  }

  @Nested
  class AllArgsConstructor {

    @Test
    void shouldAllowCreationViaAllArgsConstructor() {
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Object> metrics = Map.of("error_rate", 5.0);
      Map<String, Double> deltas = Map.of("error_rate", 10.0);
      List<String> sessions = List.of("session-1");

      RootCauseSegment segment =
          new RootCauseSegment("Android", dimensions, metrics, deltas, sessions);

      assertThat(segment.getLabel()).isEqualTo("Android");
      assertThat(segment.getDimensions()).isEqualTo(dimensions);
      assertThat(segment.getMetrics()).isEqualTo(metrics);
      assertThat(segment.getDeltas()).isEqualTo(deltas);
      assertThat(segment.getExampleSessionIds()).isEqualTo(sessions);
    }
  }

  @Nested
  class ComplexScenarios {

    @Test
    void shouldBuildSegmentWithHierarchicalDimensionsAndMultipleMetrics() {
      Map<String, String> dimensions = new HashMap<>();
      dimensions.put("Platform", "Android");
      dimensions.put("AppVersion", "3.4.5");
      dimensions.put("GeoState", "California");
      dimensions.put("DeviceModel", "Pixel 5");

      Map<String, Object> metrics = new HashMap<>();
      metrics.put("volume", 5000);
      metrics.put("apdex", 0.45);
      metrics.put("error_rate", 8.2);
      metrics.put("p95_latency_ms", 3500);

      Map<String, Double> deltas = new HashMap<>();
      deltas.put("error_rate", 25.3);
      deltas.put("apdex", -0.35);
      deltas.put("p95_latency_ms", 45.0);

      List<String> sessions = List.of("session-abc123", "session-def456");

      RootCauseSegment segment =
          RootCauseSegment.builder()
              .label("Android + App 3.4.5 + CA + Pixel 5")
              .dimensions(dimensions)
              .metrics(metrics)
              .deltas(deltas)
              .exampleSessionIds(sessions)
              .build();

      assertThat(segment.getLabel()).contains("Android").contains("3.4.5");
      assertThat(segment.getDimensions()).hasSize(4);
      assertThat(segment.getMetrics()).hasSize(4);
      assertThat(segment.getDeltas()).hasSize(3);
      assertThat(segment.getExampleSessionIds()).hasSize(2);
    }
  }
}

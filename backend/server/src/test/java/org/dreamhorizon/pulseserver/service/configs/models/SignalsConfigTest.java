package org.dreamhorizon.pulseserver.service.configs.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test class for {@link SignalsConfig}.
 * Tests all fields including customEventCollectorUrl to ensure 100% coverage.
 */
class SignalsConfigTest {

  /**
   * Tests for creating SignalsConfig instances using different constructors.
   */
  @Nested
  class TestSignalsConfigCreation {

    /**
     * Verifies that SignalsConfig can be created using the no-args constructor.
     * This ensures the @NoArgsConstructor annotation is working correctly.
     */
    @Test
    void shouldCreateWithNoArgs() {
      SignalsConfig signalsConfig = new SignalsConfig();
      assertNotNull(signalsConfig);
    }

    /**
     * Verifies that SignalsConfig can be created using the builder pattern
     * with all fields populated including customEventCollectorUrl.
     */
    @Test
    void shouldCreateWithBuilder() {
      FilterConfig filters = FilterConfig.builder()
          .mode(FilterMode.blacklist)
          .build();
      List<AttributeToDrop> attributesToDrop = new ArrayList<>();
      List<AttributeToAdd> attributesToAdd = new ArrayList<>();

      SignalsConfig signalsConfig = SignalsConfig.builder()
          .scheduleDurationMs(5000)
          .logsCollectorUrl("http://logs.example.com")
          .metricCollectorUrl("http://metrics.example.com")
          .spanCollectorUrl("http://spans.example.com")
          .customEventCollectorUrl("http://custom-events.example.com")
          .filters(filters)
          .attributesToDrop(attributesToDrop)
          .attributesToAdd(attributesToAdd)
          .build();

      assertEquals(5000, signalsConfig.getScheduleDurationMs());
      assertEquals("http://logs.example.com", signalsConfig.getLogsCollectorUrl());
      assertEquals("http://metrics.example.com", signalsConfig.getMetricCollectorUrl());
      assertEquals("http://spans.example.com", signalsConfig.getSpanCollectorUrl());
      assertEquals("http://custom-events.example.com", signalsConfig.getCustomEventCollectorUrl());
      assertEquals(filters, signalsConfig.getFilters());
      assertEquals(attributesToDrop, signalsConfig.getAttributesToDrop());
      assertEquals(attributesToAdd, signalsConfig.getAttributesToAdd());
    }

    /**
     * Verifies that SignalsConfig can be created using the all-args constructor.
     * This ensures the @AllArgsConstructor annotation is working correctly.
     */
    @Test
    void shouldCreateWithAllArgsConstructor() {
      FilterConfig filters = FilterConfig.builder().mode(FilterMode.whitelist).build();
      List<AttributeToDrop> attributesToDrop = new ArrayList<>();
      List<AttributeToAdd> attributesToAdd = new ArrayList<>();

      SignalsConfig signalsConfig = new SignalsConfig(
          filters,
          3000,
          "http://logs.example.com",
          "http://metrics.example.com",
          "http://spans.example.com",
          "http://custom-events.example.com",
          attributesToDrop,
          attributesToAdd
      );

      assertEquals(filters, signalsConfig.getFilters());
      assertEquals(3000, signalsConfig.getScheduleDurationMs());
      assertEquals("http://logs.example.com", signalsConfig.getLogsCollectorUrl());
      assertEquals("http://metrics.example.com", signalsConfig.getMetricCollectorUrl());
      assertEquals("http://spans.example.com", signalsConfig.getSpanCollectorUrl());
      assertEquals("http://custom-events.example.com", signalsConfig.getCustomEventCollectorUrl());
      assertEquals(attributesToDrop, signalsConfig.getAttributesToDrop());
      assertEquals(attributesToAdd, signalsConfig.getAttributesToAdd());
    }
  }

  /**
   * Tests for the customEventCollectorUrl field getter and setter.
   */
  @Nested
  class TestCustomEventCollectorUrl {

    /**
     * Verifies that customEventCollectorUrl can be set and retrieved correctly.
     */
    @Test
    void shouldSetAndGetCustomEventCollectorUrl() {
      SignalsConfig signalsConfig = new SignalsConfig();

      signalsConfig.setCustomEventCollectorUrl("http://custom-events.example.com");

      assertEquals("http://custom-events.example.com", signalsConfig.getCustomEventCollectorUrl());
    }

    /**
     * Verifies that customEventCollectorUrl can be set to null.
     * This ensures the field is optional and nullable.
     */
    @Test
    void shouldHandleNullCustomEventCollectorUrl() {
      SignalsConfig signalsConfig = SignalsConfig.builder()
          .customEventCollectorUrl(null)
          .build();

      assertEquals(null, signalsConfig.getCustomEventCollectorUrl());
    }

    /**
     * Verifies that customEventCollectorUrl can be set to an empty string.
     * This tests edge case handling for empty URL values.
     */
    @Test
    void shouldHandleEmptyCustomEventCollectorUrl() {
      SignalsConfig signalsConfig = new SignalsConfig();

      signalsConfig.setCustomEventCollectorUrl("");

      assertEquals("", signalsConfig.getCustomEventCollectorUrl());
    }

    /**
     * Verifies that customEventCollectorUrl can be updated after initial creation.
     */
    @Test
    void shouldUpdateCustomEventCollectorUrl() {
      SignalsConfig signalsConfig = SignalsConfig.builder()
          .customEventCollectorUrl("http://old-url.example.com")
          .build();

      signalsConfig.setCustomEventCollectorUrl("http://new-url.example.com");

      assertEquals("http://new-url.example.com", signalsConfig.getCustomEventCollectorUrl());
    }
  }

  /**
   * Tests for all other fields in SignalsConfig.
   */
  @Nested
  class TestAllFields {

    /**
     * Verifies that all fields can be set and retrieved using setters and getters.
     */
    @Test
    void shouldSetAndGetAllFields() {
      SignalsConfig signalsConfig = new SignalsConfig();
      FilterConfig filters = new FilterConfig();
      List<AttributeToDrop> attributesToDrop = new ArrayList<>();
      List<AttributeToAdd> attributesToAdd = new ArrayList<>();

      signalsConfig.setScheduleDurationMs(10000);
      signalsConfig.setLogsCollectorUrl("http://new-logs.example.com");
      signalsConfig.setMetricCollectorUrl("http://new-metrics.example.com");
      signalsConfig.setSpanCollectorUrl("http://new-spans.example.com");
      signalsConfig.setCustomEventCollectorUrl("http://new-custom-events.example.com");
      signalsConfig.setFilters(filters);
      signalsConfig.setAttributesToDrop(attributesToDrop);
      signalsConfig.setAttributesToAdd(attributesToAdd);

      assertEquals(10000, signalsConfig.getScheduleDurationMs());
      assertEquals("http://new-logs.example.com", signalsConfig.getLogsCollectorUrl());
      assertEquals("http://new-metrics.example.com", signalsConfig.getMetricCollectorUrl());
      assertEquals("http://new-spans.example.com", signalsConfig.getSpanCollectorUrl());
      assertEquals("http://new-custom-events.example.com", signalsConfig.getCustomEventCollectorUrl());
      assertEquals(filters, signalsConfig.getFilters());
      assertEquals(attributesToDrop, signalsConfig.getAttributesToDrop());
      assertEquals(attributesToAdd, signalsConfig.getAttributesToAdd());
    }

    /**
     * Verifies that scheduleDurationMs default value is 0 for a new instance.
     */
    @Test
    void shouldHaveDefaultScheduleDurationMs() {
      SignalsConfig signalsConfig = new SignalsConfig();

      assertEquals(0, signalsConfig.getScheduleDurationMs());
    }

    /**
     * Verifies that nullable URL fields return null when not set.
     */
    @Test
    void shouldReturnNullForUnsetUrlFields() {
      SignalsConfig signalsConfig = new SignalsConfig();

      assertEquals(null, signalsConfig.getLogsCollectorUrl());
      assertEquals(null, signalsConfig.getMetricCollectorUrl());
      assertEquals(null, signalsConfig.getSpanCollectorUrl());
      assertEquals(null, signalsConfig.getCustomEventCollectorUrl());
    }
  }

  /**
   * Tests for equals and hashCode methods generated by Lombok @Data.
   */
  @Nested
  class TestEqualsAndHashCode {

    /**
     * Verifies that two SignalsConfig objects with the same field values are equal.
     */
    @Test
    void shouldBeEqualForSameValues() {
      SignalsConfig config1 = SignalsConfig.builder()
          .scheduleDurationMs(5000)
          .logsCollectorUrl("http://logs.example.com")
          .customEventCollectorUrl("http://custom-events.example.com")
          .build();
      SignalsConfig config2 = SignalsConfig.builder()
          .scheduleDurationMs(5000)
          .logsCollectorUrl("http://logs.example.com")
          .customEventCollectorUrl("http://custom-events.example.com")
          .build();

      assertEquals(config1, config2);
    }

    /**
     * Verifies that two SignalsConfig objects with the same field values have the same hash code.
     */
    @Test
    void shouldHaveSameHashCodeForSameValues() {
      SignalsConfig config1 = SignalsConfig.builder()
          .scheduleDurationMs(5000)
          .customEventCollectorUrl("http://custom-events.example.com")
          .build();
      SignalsConfig config2 = SignalsConfig.builder()
          .scheduleDurationMs(5000)
          .customEventCollectorUrl("http://custom-events.example.com")
          .build();

      assertEquals(config1.hashCode(), config2.hashCode());
    }

    /**
     * Verifies equality when customEventCollectorUrl is null in both objects.
     */
    @Test
    void shouldBeEqualWhenCustomEventCollectorUrlIsNullInBoth() {
      SignalsConfig config1 = SignalsConfig.builder()
          .scheduleDurationMs(5000)
          .customEventCollectorUrl(null)
          .build();
      SignalsConfig config2 = SignalsConfig.builder()
          .scheduleDurationMs(5000)
          .customEventCollectorUrl(null)
          .build();

      assertEquals(config1, config2);
    }
  }

  /**
   * Tests for toString method generated by Lombok @Data.
   */
  @Nested
  class TestToString {

    /**
     * Verifies that toString contains the field values including customEventCollectorUrl.
     */
    @Test
    void shouldContainCustomEventCollectorUrlInToString() {
      SignalsConfig signalsConfig = SignalsConfig.builder()
          .customEventCollectorUrl("http://custom-events.example.com")
          .build();

      String toString = signalsConfig.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("customEventCollectorUrl"));
      assertTrue(toString.contains("http://custom-events.example.com"));
    }

    /**
     * Verifies that toString works correctly for an empty SignalsConfig object.
     */
    @Test
    void shouldHaveCorrectToStringForEmptyObject() {
      SignalsConfig signalsConfig = new SignalsConfig();

      String toString = signalsConfig.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("SignalsConfig"));
    }
  }
}

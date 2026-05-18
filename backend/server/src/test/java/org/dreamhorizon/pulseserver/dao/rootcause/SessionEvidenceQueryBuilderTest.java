package org.dreamhorizon.pulseserver.dao.rootcause;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionEvidenceQueryBuilderTest {

  private static final String PROJECT_ID = "test-project";
  private static final String INTERACTION_NAME = "checkout";
  private static final Instant START_TIME = Instant.parse("2025-03-01T00:00:00Z");
  private static final Instant END_TIME = Instant.parse("2025-03-08T00:00:00Z");

  @Nested
  class BuildSessionEvidenceQuery {

    @Test
    void shouldBuildQueryWithBasicParameters() {
      Map<String, String> dimensions = new HashMap<>();
      dimensions.put("Platform", "Android");
      Map<String, Double> metrics = new HashMap<>();
      metrics.put("error_rate", 5.0);
      metrics.put("apdex", 0.5);

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, metrics, 5);

      assertThat(query)
          .contains("SELECT")
          .contains("SessionId")
          .contains("error_rate")
          .contains("avg_apdex")
          .contains("avg(nullIf(ApdexScore, 0)) as avg_apdex")
          .doesNotContain("toFloat32OrNull(SpanAttributes['pulse.interaction.apdex_score'])")
          .contains("FROM otel.otel_traces")
          .contains("WHERE")
          .contains("ProjectId = 'test-project'")
          .contains("SpanName = 'checkout'")
          .contains("Platform = 'Android'")
          .contains("HAVING")
          .contains("error_rate >= 0.05")
          .contains("ifNull(avg_apdex, 0.0) <= 0.5")
          .contains("ORDER BY")
          .contains("LIMIT 5");
    }

    @Test
    void shouldHandleNullMetrics() {
      Map<String, String> dimensions = Map.of("Platform", "iOS");

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, null, 10);

      assertThat(query)
          .contains("error_rate >= 0.0")
          .contains("ifNull(avg_apdex, 0.0) <= 1.0");
    }

    @Test
    void shouldHandleNullDimensions() {
      Map<String, Double> metrics = Map.of("error_rate", 10.0, "apdex", 0.6);

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, null, metrics, 5);

      assertThat(query).contains("WHERE").contains("ProjectId").doesNotContain("Platform");
    }

    @Test
    void shouldHandleNullLimit() {
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Double> metrics = Map.of("error_rate", 3.0, "apdex", 0.7);

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, metrics, null);

      assertThat(query).contains("LIMIT 5");
    }

    @Test
    void shouldEscapeSingleQuotesInValues() {
      Map<String, String> dimensions = Map.of("DeviceModel", "iPhone's Model");
      Map<String, Double> metrics = Map.of("error_rate", 2.0, "apdex", 0.8);

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, metrics, 5);

      assertThat(query).contains("iPhone''s Model");
    }

    @Test
    void shouldConvertErrorRatePercentageToDecimal() {
      Map<String, String> dimensions = Map.of("Platform", "Android");
      Map<String, Double> metrics = Map.of("error_rate", 100.0, "apdex", 0.0);

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, metrics, 5);

      assertThat(query).contains("error_rate >= 1.0");
    }

    @Test
    void shouldFormatTimestampsCorrectly() {
      Map<String, String> dimensions = new HashMap<>();
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, metrics, 5);

      assertThat(query)
          .contains("Timestamp >= '2025-03-01 00:00:00'")
          .contains("Timestamp < '2025-03-08 00:00:00'");
    }

    @Test
    void shouldOrderByErrorCountDescThenApdexAsc() {
      Map<String, String> dimensions = new HashMap<>();
      Map<String, Double> metrics = Map.of("error_rate", 5.0, "apdex", 0.5);

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, metrics, 5);

      assertThat(query).contains("ORDER BY").contains("error_count DESC").contains("avg_apdex ASC");
    }

    @Test
    void shouldHandleMultipleDimensions() {
      Map<String, String> dimensions = new HashMap<>();
      dimensions.put("Platform", "iOS");
      dimensions.put("AppVersion", "2.5.1");
      dimensions.put("GeoState", "CA");
      Map<String, Double> metrics = Map.of("error_rate", 7.0, "apdex", 0.4);

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, metrics, 5);

      assertThat(query)
          .contains("Platform = 'iOS'")
          .contains("AppVersion = '2.5.1'")
          .contains("GeoState = 'CA'");
    }

    @Test
    void shouldUseBackwardCompatibleOverloadWithoutMetrics() {
      Map<String, String> dimensions = Map.of("Platform", "Android");

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 5);

      assertThat(query)
          .contains("error_rate >= 0.0")
          .contains("ifNull(avg_apdex, 0.0) <= 1.0");
    }
  }

  @Nested
  class BuildTotalSessionsCountQuery {

    @Test
    void shouldBuildTotalSessionsCountQuery() {
      Map<String, String> dimensions = Map.of("Platform", "Android");

      String query =
          SessionEvidenceQueryBuilder.buildTotalSessionsCountQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions);

      assertThat(query)
          .contains("SELECT uniqCombined64(nullIf(SessionId, ''))")
          .contains("FROM otel.otel_traces")
          .contains("ProjectId = 'test-project'")
          .contains("SpanName = 'checkout'")
          .contains("Platform = 'Android'")
          .contains("Timestamp >= '2025-03-01 00:00:00'")
          .contains("Timestamp < '2025-03-08 00:00:00'");
    }

    @Test
    void shouldHandleNullDimensionsInTotalSessionsCount() {
      String query =
          SessionEvidenceQueryBuilder.buildTotalSessionsCountQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, null);

      assertThat(query)
          .contains("uniqCombined64(nullIf(SessionId, ''))")
          .contains("ProjectId = 'test-project'");
    }

    @Test
    void shouldEscapeSingleQuotesInTotalSessionsQuery() {
      Map<String, String> dimensions = Map.of("DeviceModel", "Device's Name");

      String query =
          SessionEvidenceQueryBuilder.buildTotalSessionsCountQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions);

      assertThat(query).contains("Device''s Name");
    }
  }

  @Nested
  class DetermineSortOrder {

    @Test
    void shouldSortByErrorRateWhenErrorRateIsBigger() {
      Map<String, Double> deltas = Map.of("error_rate", 20.0, "apdex", 5.0);

      String sortOrder = SessionEvidenceQueryBuilder.determineSortOrder(deltas);

      assertThat(sortOrder).contains("error_rate DESC").contains("apdex_score ASC");
    }

    @Test
    void shouldSortByApdexWhenApdexIsBigger() {
      Map<String, Double> deltas = Map.of("error_rate", 5.0, "apdex", 20.0);

      String sortOrder = SessionEvidenceQueryBuilder.determineSortOrder(deltas);

      assertThat(sortOrder).contains("apdex_score ASC").contains("error_rate DESC");
    }

    @Test
    void shouldDefaultToErrorRateWhenDeltasEqual() {
      Map<String, Double> deltas = Map.of("error_rate", 10.0, "apdex", 10.0);

      String sortOrder = SessionEvidenceQueryBuilder.determineSortOrder(deltas);

      assertThat(sortOrder).contains("error_rate DESC").contains("apdex_score ASC");
    }

    @Test
    void shouldDefaultToErrorRateWhenDeltasEmpty() {
      String sortOrder = SessionEvidenceQueryBuilder.determineSortOrder(new HashMap<>());

      assertThat(sortOrder).contains("error_rate DESC").contains("apdex_score ASC");
    }

    @Test
    void shouldDefaultToErrorRateWhenDeltasNull() {
      String sortOrder = SessionEvidenceQueryBuilder.determineSortOrder(null);

      assertThat(sortOrder).contains("error_rate DESC").contains("apdex_score ASC");
    }

    @Test
    void shouldHandleNegativeDeltasUsingAbsoluteValue() {
      Map<String, Double> deltas = Map.of("error_rate", -20.0, "apdex", 5.0);

      String sortOrder = SessionEvidenceQueryBuilder.determineSortOrder(deltas);

      assertThat(sortOrder).contains("error_rate DESC").contains("apdex_score ASC");
    }
  }

  @Nested
  class DeterminePrimarySortMetric {

    @Test
    void shouldReturnErrorRateWhenErrorRateIsBigger() {
      Map<String, Double> deltas = Map.of("error_rate", 15.0, "apdex", 3.0);

      String metric = SessionEvidenceQueryBuilder.determinePrimarySortMetric(deltas);

      assertThat(metric).isEqualTo("error_rate");
    }

    @Test
    void shouldReturnApdexWhenApdexIsBigger() {
      Map<String, Double> deltas = Map.of("error_rate", 3.0, "apdex", 15.0);

      String metric = SessionEvidenceQueryBuilder.determinePrimarySortMetric(deltas);

      assertThat(metric).isEqualTo("apdex");
    }

    @Test
    void shouldDefaultToErrorRateWhenEqual() {
      Map<String, Double> deltas = Map.of("error_rate", 10.0, "apdex", 10.0);

      String metric = SessionEvidenceQueryBuilder.determinePrimarySortMetric(deltas);

      assertThat(metric).isEqualTo("error_rate");
    }

    @Test
    void shouldDefaultToErrorRateWhenEmpty() {
      String metric = SessionEvidenceQueryBuilder.determinePrimarySortMetric(new HashMap<>());

      assertThat(metric).isEqualTo("error_rate");
    }

    @Test
    void shouldDefaultToErrorRateWhenNull() {
      String metric = SessionEvidenceQueryBuilder.determinePrimarySortMetric(null);

      assertThat(metric).isEqualTo("error_rate");
    }
  }
}

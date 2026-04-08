package org.dreamhorizon.pulseserver.dao.rootcause;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SessionEvidenceQueryBuilderTest {

  private static final String PROJECT_ID = "test-project";
  private static final String INTERACTION_NAME = "checkout_flow";
  private static final Instant START_TIME = Instant.parse("2025-11-07T00:00:00Z");
  private static final Instant END_TIME = Instant.parse("2025-11-12T23:59:59Z");

  @Nested
  @DisplayName("buildSessionEvidenceQuery")
  class BuildSessionEvidenceQuery {

    @Test
    @DisplayName("should filter by interaction name")
    void shouldFilterByInteractionName() {
      Map<String, String> dimensions = new HashMap<>();
      dimensions.put("Platform", "android");

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 5);

      assertThat(query)
          .contains("SpanName = 'checkout_flow'")
          .as("query should filter by interaction name (span.name)");
    }

    @Test
    @DisplayName("should include project ID filter")
    void shouldFilterByProjectId() {
      Map<String, String> dimensions = new HashMap<>();
      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 5);

      assertThat(query)
          .contains("ProjectId = 'test-project'")
          .as("query should filter by project ID");
    }

    @Test
    @DisplayName("should include time range filters")
    void shouldFilterByTimeRange() {
      Map<String, String> dimensions = new HashMap<>();
      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 5);

      assertThat(query)
          .contains("Timestamp >= '2025-11-07T00:00:00Z'")
          .contains("Timestamp < '2025-11-12T23:59:59Z'")
          .as("query should filter by time range");
    }

    @Test
    @DisplayName("should include segment dimension filters")
    void shouldFilterBySegmentDimensions() {
      Map<String, String> dimensions = new HashMap<>();
      dimensions.put("Platform", "android");
      dimensions.put("OsVersion", "13");

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 5);

      assertThat(query)
          .contains("Platform = 'android'")
          .contains("OsVersion = '13'")
          .as("query should include segment dimensions");
    }

    @Test
    @DisplayName("should select only SessionId")
    void shouldSelectOnlySessionId() {
      Map<String, String> dimensions = new HashMap<>();
      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 5);

      assertThat(query)
          .contains("SELECT DISTINCT SessionId")
          .doesNotContain("error_rate")
          .doesNotContain("apdex_score")
          .doesNotContain("error_count")
          .as("query should select only SessionId, no metrics");
    }

    @Test
    @DisplayName("should group by session ID")
    void shouldGroupBySessionId() {
      Map<String, String> dimensions = new HashMap<>();
      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 5);

      assertThat(query)
          .doesNotContain("GROUP BY")
          .as("query should use DISTINCT instead of GROUP BY");
    }

    @Test
    @DisplayName("should respect custom limit")
    void shouldRespectCustomLimit() {
      Map<String, String> dimensions = new HashMap<>();
      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 10);

      assertThat(query).contains("LIMIT 10").as("query should use custom limit");
    }

    @Test
    @DisplayName("should use default limit when null")
    void shouldUseDefaultLimitWhenNull() {
      Map<String, String> dimensions = new HashMap<>();
      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, null);

      assertThat(query).contains("LIMIT 5").as("query should use default limit of 5");
    }

    @Test
    @DisplayName("should escape single quotes in string literals")
    void shouldEscapeQuotes() {
      Map<String, String> dimensions = new HashMap<>();
      dimensions.put("Platform", "android's");

      String query =
          SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions, 5);

      assertThat(query).contains("Platform = 'android''s'").as("query should escape quotes");
    }
  }

  @Nested
  @DisplayName("buildTotalSessionsCountQuery")
  class BuildTotalSessionsCountQuery {

    @Test
    @DisplayName("should build count query with correct filters")
    void shouldBuildCountQuery() {
      Map<String, String> dimensions = new HashMap<>();
      dimensions.put("Platform", "android");

      String query =
          SessionEvidenceQueryBuilder.buildTotalSessionsCountQuery(
              PROJECT_ID, INTERACTION_NAME, START_TIME, END_TIME, dimensions);

      assertThat(query)
          .contains("uniqCombined64(nullIf(SessionId, ''))")
          .contains("SpanName = 'checkout_flow'")
          .contains("Platform = 'android'")
          .as("count query should have uniqCombined64 and proper filters");
    }
  }
}

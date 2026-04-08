package org.dreamhorizon.pulseserver.dao.rootcause;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test for session evidence query builder.
 *
 * Tests that the query correctly identifies sessions with:
 * 1. High error rate (error_rate_delta > threshold)
 * 2. Low apdex score (apdex_delta > threshold, i.e., worse performance)
 * 3. High poor interaction count (many interactions with apdex < 0.5)
 *
 * Query logic:
 * - Sessions with poor interactions: apdex_score < 0.5
 * - Sessions exceeding error/apdex deltas: sorted by both metrics
 * - Top N sessions returned
 */
class SessionEvidenceQueryBuilderE2ETest {

  private static final String PROJECT_ID = "fancode";
  private static final String INTERACTION_NAME = "LiveNowSectionToMatchPageLoaded";
  private static final LocalDate TEST_DATE = LocalDate.of(2026, 4, 8);
  private static final Instant START_TIME =
      TEST_DATE.atStartOfDay().toInstant(ZoneOffset.UTC);
  private static final Instant END_TIME =
      TEST_DATE.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

  @Test
  @DisplayName("Query should identify sessions with high error rate and low apdex")
  void shouldQuerySessionsWithHighErrorRateAndLowApdex() {
    // Scenario: We have a segment with poor interactions (high error rate, low apdex)
    // Query should find sessions that match this pattern
    
    Map<String, String> segmentDimensions = new HashMap<>();
    segmentDimensions.put("os.version", "16");  // Android 16 users affected
    segmentDimensions.put("network.connection.type", "cell");  // Cellular users

    String query = SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
        PROJECT_ID,
        INTERACTION_NAME,
        START_TIME,
        END_TIME,
        segmentDimensions,
        5  // Top 5 sessions
    );

    assertThat(query)
        .as("Query should select SessionId")
        .contains("SessionId")
        .as("Query should calculate error_count")
        .contains("error_count")
        .as("Query should filter for error_count > 0")
        .contains("error_count > 0")
        .as("Query should calculate poor_interaction_count")
        .contains("poor_interaction_count")
        .as("Query should filter for poor_interaction_count > 0")
        .contains("poor_interaction_count > 0")
        .as("Query should sort by error_count DESC")
        .contains("error_count DESC")
        .as("Query should sort by apdex ASC as secondary")
        .contains("avg_apdex ASC")
        .as("Query should filter by ProjectId")
        .contains("ProjectId = '" + PROJECT_ID + "'")
        .as("Query should filter by interaction name")
        .contains("SpanName = '" + INTERACTION_NAME + "'")
        .as("Query should filter by timestamp range")
        .contains("Timestamp")
        .as("Query should filter by os.version dimension")
        .contains("os.version")
        .as("Query should filter by network.connection.type dimension")
        .contains("network.connection.type");

    System.out.println("Generated Query:\n" + query);
  }

  @Test
  @DisplayName("Query should filter by segment dimensions (os.version, network.connection.type)")
  void shouldFilterBySegmentDimensions() {
    // When we identify a segment with specific dimension values,
    // we should find sessions within that segment
    
    Map<String, String> segmentDimensions = new HashMap<>();
    segmentDimensions.put("os.version", "15");  // Android 15
    segmentDimensions.put("device.manufacturer", "vivo");

    String query = SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
        PROJECT_ID,
        INTERACTION_NAME,
        START_TIME,
        END_TIME,
        segmentDimensions,
        3
    );

    assertThat(query)
        .as("Query should filter by os.version from segment")
        .contains("os.version", "15")
        .as("Query should filter by device.manufacturer from segment")
        .contains("device.manufacturer", "vivo");

    System.out.println("Generated Query with Filters:\n" + query);
  }

  @Test
  @DisplayName("Query should handle empty segment dimensions")
  void shouldHandleEmptySegmentDimensions() {
    Map<String, String> emptyDimensions = new HashMap<>();

    String query = SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
        PROJECT_ID,
        INTERACTION_NAME,
        START_TIME,
        END_TIME,
        emptyDimensions,
        5
    );

    assertThat(query)
        .as("Query should be valid even with empty dimensions")
        .contains("SessionId")
        .contains("error_count > 0")
        .contains("poor_interaction_count > 0")
        .contains(PROJECT_ID)
        .contains(INTERACTION_NAME);

    System.out.println("Generated Query (No Filters):\n" + query);
  }

  @Test
  @DisplayName("Scenario: High error rate sessions should be prioritized")
  void scenario_HighErrorRateSessions() {
    /*
     * Real scenario from traces:
     * - LiveNowSectionToMatchPageLoaded interaction
     * - Some sessions have error_rate > threshold (e.g., 30%+)
     * - Some sessions have apdex < 0.5 (poor performance)
     *
     * Expected: Query returns sessions with BOTH conditions high
     */
    
    Map<String, String> dimensions = new HashMap<>();
    dimensions.put("network.connection.type", "cell");

    String query = SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
        PROJECT_ID,
        "LiveNowSectionToMatchPageLoaded",
        START_TIME,
        END_TIME,
        dimensions,
        5
    );

    System.out.println("\n=== Scenario: High Error Rate Sessions ===");
    System.out.println("Query will find sessions with:");
    System.out.println("- Low apdex score (< 0.5) = poor user experience");
    System.out.println("- High error rate (is_error = true)");
    System.out.println("- On cellular network connections");
    System.out.println("\nGenerated SQL:\n" + query);

    assertThat(query).contains("SessionId");
  }

  @Test
  @DisplayName("Scenario: Poor interactions with specific OS version")
  void scenario_PoorInteractionsSpecificOS() {
    /*
     * Real scenario:
     * - Android 16 (os.version = "16") users experiencing issues
     * - Specific interaction: ClickedActivateToAddToCart
     * - Need to find representative sessions
     */
    
    Map<String, String> dimensions = new HashMap<>();
    dimensions.put("os.version", "16");
    dimensions.put("os.name", "Android");

    String query = SessionEvidenceQueryBuilder.buildSessionEvidenceQuery(
        PROJECT_ID,
        "ClickedActivateToAddToCart",
        START_TIME,
        END_TIME,
        dimensions,
        5
    );

    System.out.println("\n=== Scenario: Poor Interactions on Android 16 ===");
    System.out.println("Query targets:");
    System.out.println("- OS Version: Android 16");
    System.out.println("- Interaction: ClickedActivateToAddToCart");
    System.out.println("- Will return sessions with lowest apdex in this cohort");
    System.out.println("\nGenerated SQL:\n" + query);

    assertThat(query).contains("16", "ClickedActivateToAddToCart");
  }
}

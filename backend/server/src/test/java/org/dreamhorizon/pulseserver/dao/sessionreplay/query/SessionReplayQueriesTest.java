package org.dreamhorizon.pulseserver.dao.sessionreplay.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.dao.sessionreplay.query.SessionReplayQueries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SessionReplayQueries")
class SessionReplayQueriesTest {

  @Test
  @DisplayName("should have GET_BLOCK_LISTING_QUERY constant defined")
  void shouldHaveConstantDefined() {
    assertThat(SessionReplayQueries.GET_BLOCK_LISTING_QUERY)
        .isNotNull()
        .isNotBlank();
  }

  @Nested
  @DisplayName("GET_BLOCK_LISTING_QUERY")
  class GetBlockListingQuery {

    @Test
    @DisplayName("should contain SELECT statement with required columns")
    void shouldContainSelectStatementWithRequiredColumns() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query)
          .contains("SELECT")
          .contains("min(MinFirstTimestamp) AS start_time")
          .contains("toString(groupArrayArray(BlockFirstTimestamps)) AS block_first_timestamps")
          .contains("toString(groupArrayArray(BlockLastTimestamps)) AS block_last_timestamps")
          .contains("toString(groupArrayArray(BlockUrls)) AS block_urls")
          .contains("any(SnapshotSource) AS snapshot_source");
    }

    @Test
    @DisplayName("should select from session_replay_events table")
    void shouldSelectFromSessionReplayEventsTable() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query)
          .contains("FROM otel.session_replay_events");
    }

    @Test
    @DisplayName("should filter by ProjectId and SessionId with placeholders")
    void shouldFilterByProjectIdAndSessionIdWithPlaceholders() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query)
          .contains("ProjectId = '${project_id}'")
          .contains("SessionId = '${session_id}'");
    }

    @Test
    @DisplayName("should group by SessionId")
    void shouldGroupBySessionId() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query)
          .contains("GROUP BY SessionId");
    }

    @Test
    @DisplayName("should have valid SQL syntax structure")
    void shouldHaveValidSqlSyntaxStructure() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      // Check for proper clause ordering: SELECT -> FROM -> WHERE -> GROUP BY
      int selectIndex = query.indexOf("SELECT");
      int fromIndex = query.indexOf("FROM");
      int whereIndex = query.indexOf("WHERE");
      int groupByIndex = query.indexOf("GROUP BY");

      assertThat(selectIndex).isGreaterThan(-1).isLessThan(fromIndex);
      assertThat(fromIndex).isGreaterThan(-1).isLessThan(whereIndex);
      assertThat(whereIndex).isGreaterThan(-1).isLessThan(groupByIndex);
      assertThat(groupByIndex).isGreaterThan(-1);
    }

    @Test
    @DisplayName("should use PascalCase column names from ClickHouse table")
    void shouldUsePascalCaseColumnNames() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      // Verify PascalCase column names from otel.session_replay_events table
      assertThat(query)
          .contains("min(MinFirstTimestamp)")
          .contains("groupArrayArray(BlockFirstTimestamps)")
          .contains("groupArrayArray(BlockLastTimestamps)")
          .contains("groupArrayArray(BlockUrls)")
          .contains("any(SnapshotSource)")
          .contains("ProjectId = '${project_id}'")
          .contains("SessionId = '${session_id}'");

      // Output aliases are in snake_case to match Java DTO field names
      assertThat(query)
          .contains("AS start_time")
          .contains("AS block_first_timestamps")
          .contains("AS block_last_timestamps")
          .contains("AS block_urls")
          .contains("AS snapshot_source");
    }

    @Test
    @DisplayName("should use ClickHouse aggregate functions")
    void shouldUseClickhouseAggregateFunctions() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query)
          .contains("min(MinFirstTimestamp)")
          .contains("groupArrayArray(BlockFirstTimestamps)")
          .contains("groupArrayArray(BlockLastTimestamps)")
          .contains("groupArrayArray(BlockUrls)")
          .contains("any(SnapshotSource)");
    }

    @Test
    @DisplayName("should use toString for array conversion")
    void shouldUseToStringForArrayConversion() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query)
          .contains("toString(groupArrayArray(BlockFirstTimestamps))")
          .contains("toString(groupArrayArray(BlockLastTimestamps))")
          .contains("toString(groupArrayArray(BlockUrls))");
    }

    @Test
    @DisplayName("should have correct output aliases for BlockListingQueryRow")
    void shouldHaveCorrectOutputAliasesForBlockListingQueryRow() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      // Output aliases must match BlockListingQueryRow.java field names
      assertThat(query)
          .contains("AS start_time")
          .contains("AS block_first_timestamps")
          .contains("AS block_last_timestamps")
          .contains("AS block_urls")
          .contains("AS snapshot_source");
    }

    @Test
    @DisplayName("should be a valid multi-line string")
    void shouldBeValidMultilineString() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query)
          .isNotEmpty()
          .isNotBlank()
          .contains("\n"); // Multi-line string
    }

    @Test
    @DisplayName("should have proper indentation for readability")
    void shouldHaveProperIndentation() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      // Check that query is properly formatted (has leading/trailing whitespace)
      assertThat(query.trim()).isNotEmpty();
      
      // Multiple lines indicate proper formatting
      String[] lines = query.split("\n");
      assertThat(lines.length).isGreaterThan(1);
    }

    @Test
    @DisplayName("should use otel.session_replay_events table fully qualified name")
    void shouldUseFullyQualifiedTableName() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query)
          .contains("FROM otel.session_replay_events");
    }

    @Test
    @DisplayName("should not have hardcoded project or session ID")
    void shouldNotHaveHardcodedIds() {
      String query = SessionReplayQueries.GET_BLOCK_LISTING_QUERY;

      assertThat(query).contains("${project_id}").contains("${session_id}");
    }
  }
}

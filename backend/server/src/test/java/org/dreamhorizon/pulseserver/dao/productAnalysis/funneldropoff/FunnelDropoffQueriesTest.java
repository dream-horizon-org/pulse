package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FunnelDropoffQueriesTest {

  @Nested
  class BuildCausesSql {

    @Test
    void shouldAnchorOnSessionStateWhenModeIsSessions() {
      String sql = FunnelDropoffQueries.buildCausesSql(
          "p1", 42L, 2, "2026-04-23 10:00:00", "SESSIONS");
      assertThat(sql).contains("otel.funnel_session_state");
      assertThat(sql).doesNotContain("otel.funnel_user_state");
      assertThat(sql).contains("DropoffStep = 3"); // stepIndex 2 → dropoff step 3
      assertThat(sql).contains("DropoffStep = -1"); // converter anchor
      assertThat(sql).contains("FunnelId = 42");
    }

    @Test
    void shouldAnchorOnUserStateWhenModeIsUniqueUsers() {
      String sql = FunnelDropoffQueries.buildCausesSql(
          "p1", 42L, 0, "2026-04-23 10:00:00", "UNIQUE_USERS");
      assertThat(sql).contains("otel.funnel_user_state");
      assertThat(sql).contains("CanonicalSessionId AS SessionId");
      assertThat(sql).contains("DropoffStep = 1");
    }

    @Test
    void shouldIncludeStackAndHttpAndFrameCauses() {
      String sql = FunnelDropoffQueries.buildCausesSql(
          "p1", 1L, 0, "2026-04-23 10:00:00", "SESSIONS");
      assertThat(sql).contains("stack_trace_events");
      assertThat(sql).contains("otel_traces");
      assertThat(sql).contains("session_summary");
      assertThat(sql).contains("http_5xx");
      assertThat(sql).contains("http_4xx");
      assertThat(sql).contains("frozen_frame");
    }

    @Test
    void shouldOrderByLiftDescending() {
      String sql = FunnelDropoffQueries.buildCausesSql(
          "p1", 1L, 0, "2026-04-23 10:00:00", "SESSIONS");
      assertThat(sql).contains("ORDER BY lift DESC");
      assertThat(sql).contains("LIMIT 50");
    }

    @Test
    void shouldFallBackToMaxRunTimeWhenRunTimeIsNull() {
      String sql = FunnelDropoffQueries.buildCausesSql(
          "p1", 99L, 0, null, "SESSIONS");
      assertThat(sql).contains("SELECT max(RunTime) FROM otel.funnel_results");
      assertThat(sql).contains("FunnelId = 99");
    }
  }

  @Nested
  class BuildEvidenceSql {

    @Test
    void shouldPickSessionStateColumnsForSessionsMode() {
      String sql = FunnelDropoffQueries.buildEvidenceSql(
          "p1", 7L, 3, "2026-04-23 10:00:00", "SESSIONS",
          List.of("s-1", "s-2"));
      assertThat(sql).contains("otel.funnel_session_state");
      assertThat(sql).contains("SessionId AS sessionId");
      assertThat(sql).contains("LastReachedAt");
      assertThat(sql).contains("'s-1'");
      assertThat(sql).contains("'s-2'");
    }

    @Test
    void shouldPickUserStateColumnsForUniqueUsersMode() {
      String sql = FunnelDropoffQueries.buildEvidenceSql(
          "p1", 7L, 0, "2026-04-23 10:00:00", "UNIQUE_USERS",
          List.of("s-1"));
      assertThat(sql).contains("otel.funnel_user_state");
      assertThat(sql).contains("CanonicalSessionId AS sessionId");
      assertThat(sql).contains("CanonicalLastReachedAt");
    }

    @Test
    void shouldEscapeSingleQuotesInSessionIds() {
      String sql = FunnelDropoffQueries.buildEvidenceSql(
          "p1", 1L, 0, "2026-04-23 10:00:00", "SESSIONS",
          List.of("bad'id"));
      // quote is doubled ('' is ClickHouse's escape for ')
      assertThat(sql).contains("bad''id");
    }
  }

  @Nested
  class BuildCausesSqlFromAttribution {

    @Test
    void shouldReadFromPrecomputedAttributionTable() {
      String sql = FunnelDropoffQueries.buildCausesSqlFromAttribution(
          "p1", 7L, 3, "2026-04-23 10:00:00");
      assertThat(sql)
          .contains("FROM otel.funnel_dropoff_attribution")
          .doesNotContain("INNER JOIN otel.stack_trace_events")
          .doesNotContain("INNER JOIN otel.otel_traces")
          .doesNotContain("INNER JOIN otel.session_summary");
    }

    @Test
    void shouldFilterByFunnelStepAndRunTime() {
      String sql = FunnelDropoffQueries.buildCausesSqlFromAttribution(
          "p1", 7L, 3, "2026-04-23 10:00:00");
      // stepIndex=3 means "dropped from step 3" → attribution table stores StepIndex = 4
      // (the step the user failed to reach).
      assertThat(sql)
          .contains("ProjectId = 'p1'")
          .contains("FunnelId = 7")
          .contains("StepIndex = 4")
          .contains("toDateTime64('2026-04-23 10:00:00', 3, 'UTC')");
    }

    @Test
    void shouldOrderByLiftDescAndCapAt50Rows() {
      String sql = FunnelDropoffQueries.buildCausesSqlFromAttribution(
          "p1", 7L, 3, "2026-04-23 10:00:00");
      assertThat(sql)
          .contains("ORDER BY lift DESC, dropoffAffected DESC")
          .contains("LIMIT 50");
    }

    @Test
    void shouldConvertExampleSessionsArrayToCsvForDaoMapping() {
      // CauseRow.exampleSessions is a String (comma-joined). The precomputed table
      // stores Array(String), so the SELECT must convert via arrayStringConcat.
      String sql = FunnelDropoffQueries.buildCausesSqlFromAttribution(
          "p1", 7L, 3, "2026-04-23 10:00:00");
      assertThat(sql).contains("arrayStringConcat(ExampleSessions, ',') AS exampleSessions");
    }

    @Test
    void shouldFallBackToMaxRunTimeWhenRunTimeIsNull() {
      String sql = FunnelDropoffQueries.buildCausesSqlFromAttribution(
          "p1", 7L, 3, null);
      assertThat(sql).contains("SELECT max(RunTime) FROM otel.funnel_results");
    }
  }
}

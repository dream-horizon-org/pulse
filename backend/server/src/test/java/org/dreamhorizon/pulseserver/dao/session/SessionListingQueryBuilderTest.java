package org.dreamhorizon.pulseserver.dao.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionListingQueryBuilderTest {

  private static final String PROJECT_ID = "project-1";
  private static final String START = "2024-01-01T00:00:00Z";
  private static final String END = "2024-01-01T23:59:59Z";

  @Nested
  class BuildListingQuery {

    @Test
    void shouldBuildBasicListingQuery() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END)
          .limit(10);

      String sql = builder.buildListingQuery();

      assertThat(sql).contains("SELECT");
      assertThat(sql).contains("FROM otel.session_summary");
      assertThat(sql).contains("WHERE ProjectId =");
      assertThat(sql).contains("GROUP BY sessionId");
      assertThat(sql).contains("ORDER BY");
      assertThat(sql).contains("LIMIT 10");
      assertThat(sql).contains(PROJECT_ID);
    }

    @Test
    void shouldIncludeSearchInWhereWhenSearchSet() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END)
          .search("user-123");

      String sql = builder.buildListingQuery();

      assertThat(sql).contains("userId =");
      assertThat(sql).contains("sessionId =");
    }

    @Test
    void shouldIncludeQuickFilterInHaving() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END)
          .quickFilters(EnumSet.of(QuickFilter.SLOW));

      String sql = builder.buildListingQuery();

      assertThat(sql).contains("HAVING");
      assertThat(sql).contains("sum(slowInteractionCount) > 0");
    }

    @Test
    void shouldThrowWhenProjectIdMissing() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .timeRange(START, END);

      assertThatThrownBy(builder::buildListingQuery)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("projectId");
    }

    @Test
    void shouldThrowWhenTimeRangeMissing() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID);

      assertThatThrownBy(builder::buildListingQuery)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("timeRange");
    }
  }

  @Nested
  class BuildJourneyQuery {

    @Test
    void shouldBuildJourneyQueryWithSessionIds() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END);

      String sql = builder.buildJourneyQuery(List.of("s1", "s2"));

      assertThat(sql).contains("SessionId");
      assertThat(sql).contains("FROM otel.otel_traces");
      assertThat(sql).contains("SessionId IN (");
      assertThat(sql).contains("'s1'");
      assertThat(sql).contains("'s2'");
      assertThat(sql).contains("GROUP BY SessionId");
    }

    @Test
    void shouldThrowWhenSessionIdsEmpty() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END);

      assertThatThrownBy(() -> builder.buildJourneyQuery(Collections.emptyList()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sessionIds must not be empty");
    }

    @Test
    void shouldThrowWhenSessionIdsNull() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END);

      assertThatThrownBy(() -> builder.buildJourneyQuery(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sessionIds must not be empty");
    }
  }

  @Nested
  class BuildImpactedScreensQuery {

    @Test
    void shouldBuildImpactedScreensQuery() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END);

      String sql = builder.buildImpactedScreensQuery(List.of("s1"));

      assertThat(sql).contains("FROM otel.stack_trace_events");
      assertThat(sql).contains("SessionId IN (");
      assertThat(sql).contains("crashScreens");
      assertThat(sql).contains("anrScreens");
      assertThat(sql).contains("nonFatalScreens");
      assertThat(sql).contains("GROUP BY SessionId");
    }

    @Test
    void shouldThrowWhenSessionIdsEmpty() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END);

      assertThatThrownBy(() -> builder.buildImpactedScreensQuery(Collections.emptyList()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sessionIds must not be empty");
    }
  }

  @Nested
  class BuildImpactedInteractionsQuery {

    @Test
    void shouldBuildImpactedInteractionsQuery() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END);

      String sql = builder.buildImpactedInteractionsQuery(List.of("s1", "s2"));

      assertThat(sql).contains("FROM otel.otel_traces");
      assertThat(sql).contains("SessionId IN (");
      assertThat(sql).contains("'s1'");
      assertThat(sql).contains("'s2'");
      assertThat(sql).contains("PulseType = 'interaction'");
      assertThat(sql).contains("impactedInteractionNames");
      assertThat(sql).contains("GROUP BY SessionId");
    }

    @Test
    void shouldThrowWhenSessionIdsEmpty() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END);

      assertThatThrownBy(() -> builder.buildImpactedInteractionsQuery(Collections.emptyList()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sessionIds must not be empty");
    }
  }

  @Nested
  class RequiresSemiJoin {

    @Test
    void shouldReturnFalseWhenOnlyHavingFilters() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END)
          .filter(FilterField.DURATION, Operator.GT, 1000);

      assertThat(builder.requiresSemiJoin()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenNonMvWhereFilterPresent() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END)
          .filter(FilterField.INTERACTION_NAME, Operator.EQ, "Tap");

      assertThat(builder.requiresSemiJoin()).isTrue();
    }
  }

  @Nested
  class CursorAndSort {

    @Test
    void shouldIncludeCursorInHaving() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END)
          .cursor(new CursorCodec.CursorValue("2024-01-01T11:00:00Z", "prev-session"));

      String sql = builder.buildListingQuery();

      assertThat(sql).contains("HAVING");
      assertThat(sql).contains("sessionId");
    }

    @Test
    void shouldApplySortByAndDirection() {
      SessionListingQueryBuilder builder = SessionListingQueryBuilder.create()
          .projectId(PROJECT_ID)
          .timeRange(START, END)
          .sortBy(SortField.DURATION, SortDirection.ASC);

      String sql = builder.buildListingQuery();

      assertThat(sql).contains("dateDiff('millisecond'");
      assertThat(sql).contains("ASC");
    }
  }
}

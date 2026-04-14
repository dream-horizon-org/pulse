package org.dreamhorizon.pulseserver.dao.rootcause;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RootCauseCacheQueriesTest {

  private static final Instant WINDOW_END =
      LocalDateTime.of(2025, 6, 1, 15, 30, 45, 123_000_000).toInstant(ZoneOffset.UTC);

  @Nested
  class BuildSelectByKeyQuery {

    @Test
    void shouldContainEscapedProjectInteractionAndDate() {
      String sql =
          RootCauseCacheQueries.buildSelectByKeyQuery("p1", "tap_pay", "2025-06-01");
      assertThat(sql).startsWith(RootCauseCacheQueries.SELECT_FROM_ROOT_CAUSE_CACHE);
      assertThat(sql).contains("WHERE ProjectId = 'p1'");
      assertThat(sql).contains("AND interaction_name = 'tap_pay'");
      assertThat(sql).contains("AND date = '2025-06-01'");
    }

    @Test
    void shouldEscapeSingleQuotesInLiterals() {
      String sql =
          RootCauseCacheQueries.buildSelectByKeyQuery(
              "proj'O", "click'here", "2025-01-02");
      assertThat(sql).contains("ProjectId = 'proj\\'O'");
      assertThat(sql).contains("interaction_name = 'click\\'here'");
    }

    @Test
    void shouldEscapeBackslashesBeforeQuotes() {
      String sql =
          RootCauseCacheQueries.buildSelectByKeyQuery("a\\b", "x", "2025-01-01");
      assertThat(sql).contains("ProjectId = 'a\\\\b'");
    }

    @Test
    void shouldTreatNullProjectAndInteractionAsEmptyString() {
      String sql = RootCauseCacheQueries.buildSelectByKeyQuery(null, null, "2025-01-01");
      assertThat(sql).contains("ProjectId = ''");
      assertThat(sql).contains("interaction_name = ''");
    }
  }

  @Nested
  class BuildInsertQuery {

    @Test
    void shouldBuildValuesTupleWithDateTime64Literals() {
      LocalDateTime cachedAt = LocalDateTime.of(2025, 6, 1, 12, 0, 5);
      String sql =
          RootCauseCacheQueries.buildInsertQuery(
              "proj",
              "checkout",
              "2025-06-01",
              WINDOW_END,
              "hierarchical",
              "{\"k\":1}",
              "[]",
              cachedAt);
      assertThat(sql).startsWith(RootCauseCacheQueries.INSERT_INTO_ROOT_CAUSE_CACHE + "(");
      assertThat(sql).contains("'proj'");
      assertThat(sql).contains("'checkout'");
      assertThat(sql).contains("'2025-06-01'");
      assertThat(sql).contains("toDateTime64('2025-06-01 15:30:45.123', 3, 'UTC')");
      assertThat(sql).contains("'hierarchical'");
      assertThat(sql).contains("toDateTime64('2025-06-01 12:00:05', 3, 'UTC')");
    }

    @Test
    void shouldEscapeJsonPayloadsForClickhouseStringLiterals() {
      String baseline = "{\"msg\":\"it's\"}";
      String segments = "[{\"l\":\"a'b\"}]";
      String sql =
          RootCauseCacheQueries.buildInsertQuery(
              "p",
              "i",
              "2025-01-01",
              WINDOW_END,
              "flat",
              baseline,
              segments,
              LocalDateTime.of(2025, 1, 1, 0, 0));
      assertThat(sql).contains("'{\"msg\":\"it\\'s\"}'");
      assertThat(sql).contains("'[{\"l\":\"a\\'b\"}]'");
    }

    @Test
    void shouldUseEmptyJsonObjectWhenBaselineOrSegmentsNull() {
      String sql =
          RootCauseCacheQueries.buildInsertQuery(
              "p",
              "i",
              "2025-01-01",
              WINDOW_END,
              "flat",
              null,
              null,
              LocalDateTime.of(2025, 1, 1, 0, 0));
      // escapeJson(null) -> "{}" for both JSON columns
      assertThat(sql).contains("'{}','{}'");
    }

    @Test
    void shouldEscapeModeWireValue() {
      String sql =
          RootCauseCacheQueries.buildInsertQuery(
              "p",
              "i",
              "2025-01-01",
              WINDOW_END,
              "flat'x",
              "{}",
              "[]",
              LocalDateTime.of(2025, 1, 1, 0, 0));
      assertThat(sql).contains("'flat\\'x'");
    }
  }

  @Nested
  class Constants {

    @Test
    void selectConstantShouldListExpectedColumns() {
      assertThat(RootCauseCacheQueries.SELECT_FROM_ROOT_CAUSE_CACHE)
          .contains("ProjectId")
          .contains("interaction_name")
          .contains("window_end_utc")
          .contains("otel.root_cause_cache");
    }

    @Test
    void insertConstantShouldListInsertColumns() {
      assertThat(RootCauseCacheQueries.INSERT_INTO_ROOT_CAUSE_CACHE)
          .contains("INSERT INTO otel.root_cause_cache")
          .contains("cached_at")
          .contains("VALUES ");
    }
  }
}

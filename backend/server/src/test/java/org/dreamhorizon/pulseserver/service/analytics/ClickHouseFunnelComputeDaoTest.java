package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClickHouseFunnelComputeDaoTest {

  private static final String PROJECT_ID = "proj-abc";

  private FunnelDefinitionRow.FunnelDefinitionRowBuilder baseRow() {
    return FunnelDefinitionRow.builder()
        .id(42L)
        .projectId(PROJECT_ID)
        .name("Test Funnel")
        .funnelType("AUTO")
        .mode("UNIQUE_USERS")
        .dateRangeDays(7)
        .windowSeconds(3600L)
        .stepsJson("[{\"eventName\":\"screen_view\"},{\"eventName\":\"add_to_cart\"},{\"eventName\":\"purchase\"}]")
        .filtersJson(null);
  }

  @Nested
  class BuildInsertSql {

    @Test
    void shouldContainInsertIntoFunnelResults() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql).contains("INSERT INTO otel.funnel_results");
    }

    @Test
    void shouldReadFromOtelLogs() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql).contains("FROM otel.otel_logs");
    }

    @Test
    void shouldFilterByProjectId() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql).contains("ResourceAttributes['project.id'] = '" + PROJECT_ID + "'");
    }

    @Test
    void shouldFilterByPulseType() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql).contains("LogAttributes['pulse.type'] = 'custom_event'");
    }

    @Test
    void shouldIncludeWindowFunnelWithCorrectWindowSeconds() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql).contains("windowFunnel(3600)");
    }

    @Test
    void shouldCastDateTime64ToDateTimeForWindowFunnel() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql)
          .contains("toDateTime(Timestamp) AS FunnelTs")
          .contains("windowFunnel(3600)(FunnelTs,");
    }

    @Test
    void shouldIncludeAllStepEventNames() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql)
          .contains("Body = 'screen_view'")
          .contains("Body = 'add_to_cart'")
          .contains("Body = 'purchase'");
    }

    @Test
    void shouldIncludeFunnelId() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql).contains("42");
    }

    @Test
    void shouldUseUserIdGroupKeyForUniqueUsers() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(
          baseRow().mode("UNIQUE_USERS").build());
      assertThat(sql).contains("LogAttributes['user.id']");
    }

    @Test
    void shouldUseSessionIdGroupKeyForSessions() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(
          baseRow().mode("SESSIONS").build());
      assertThat(sql).contains("LogAttributes['session.id']");
    }

    @Test
    void shouldUseIntervalForAutoMode() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(
          baseRow().funnelType("AUTO").dateRangeDays(14).build());
      assertThat(sql).contains("INTERVAL 14 DAY");
    }

    @Test
    void shouldUseToDateTime64ForOnceMode() {
      Instant start = Instant.parse("2024-03-01T00:00:00Z");
      Instant end   = Instant.parse("2024-03-31T23:59:59Z");
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(
          baseRow().funnelType("ONCE").startTime(start).endTime(end).build());
      assertThat(sql)
          .contains("toDateTime64('2024-03-01 00:00:00', 9)")
          .contains("toDateTime64('2024-03-31 23:59:59', 9)");
    }

    @Test
    void shouldAppendGlobalFilterClauses() {
      String filtersJson = "[{\"field\":\"OS_NAME\",\"operator\":\"EQ\",\"value\":[\"Android\"]}]";
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().filtersJson(filtersJson).build());
      assertThat(sql).contains("AND ResourceAttributes['os.name'] = 'Android'");
    }

    @Test
    void shouldEscapeSingleQuotesInEventNames() {
      String stepsJson = "[{\"eventName\":\"O'Brien's Event\"}]";
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().stepsJson(stepsJson).build());
      assertThat(sql).contains("O\\'Brien\\'s Event");
    }

    @Test
    void shouldReturnEmptyStringForNullStepsJson() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().stepsJson(null).build());
      assertThat(sql).contains("windowFunnel(3600)");
    }
  }

  @Nested
  class BuildBatchInsertSql {

    @Test
    void shouldReturnEmptyStringForEmptyList() {
      assertThat(ClickHouseFunnelComputeDao.buildBatchInsertSql(List.of())).isEmpty();
    }

    @Test
    void shouldContainInsertIntoFunnelResults() {
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(List.of(baseRow().build()));
      assertThat(sql).contains("INSERT INTO otel.funnel_results");
    }

    @Test
    void shouldContainSharedRawCte() {
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(List.of(baseRow().build()));
      assertThat(sql)
          .contains("raw AS (")
          .contains("FROM otel.otel_logs")
          .contains("LogAttributes['pulse.type'] = 'custom_event'");
    }

    @Test
    void shouldUseMaxDaysAcrossAllFunnels() {
      List<FunnelDefinitionRow> defs = List.of(
          baseRow().id(1L).dateRangeDays(7).build(),
          baseRow().id(2L).dateRangeDays(30).build()
      );
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(defs);
      assertThat(sql).contains("INTERVAL 30 DAY");
    }

    @Test
    void shouldCreateOneCtePerFunnel() {
      List<FunnelDefinitionRow> defs = List.of(
          baseRow().id(1L).build(),
          baseRow().id(2L).build()
      );
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(defs);
      assertThat(sql)
          .contains("lvl_f0")
          .contains("lvl_f1");
    }

    @Test
    void shouldContainUnionAllForMultipleFunnels() {
      List<FunnelDefinitionRow> defs = List.of(
          baseRow().id(1L).build(),
          baseRow().id(2L).build()
      );
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(defs);
      assertThat(sql).contains("UNION ALL");
    }

    @Test
    void shouldAddTighterTimestampFilterForShorterFunnel() {
      List<FunnelDefinitionRow> defs = List.of(
          baseRow().id(1L).dateRangeDays(30).build(),
          baseRow().id(2L).dateRangeDays(7).build()
      );
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(defs);
      assertThat(sql).contains("INTERVAL 7 DAY");
    }

    @Test
    void shouldUseSessionIdForSessionsFunnel() {
      FunnelDefinitionRow def = baseRow().mode("SESSIONS").build();
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(List.of(def));
      assertThat(sql).contains("SessionId");
    }

    @Test
    void shouldEmitStepRowsEvenWhenLevelsEmpty() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql)
          .contains("step_counts AS (")
          .contains("LEFT JOIN step_counts sc ON drv.number = sc.number")
          .contains("FROM (SELECT arrayJoin(range(3)) AS number) AS drv")
          .contains("greatest((SELECT countIf(lvl >= 1) FROM levels), 1)");
    }

    @Test
    void shouldCastDateTime64ToDateTimeForWindowFunnelInBatch() {
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(List.of(baseRow().build()));
      assertThat(sql)
          .contains("toDateTime(Timestamp) AS FunnelTs")
          .contains("windowFunnel(3600)(FunnelTs,");
    }

    @Test
    void shouldEmitStepRowsFromArrayJoinEvenWhenLevelCteEmpty() {
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(List.of(baseRow().build()));
      assertThat(sql)
          .contains("FROM (SELECT arrayJoin(range(3)) AS number) AS drv")
          .contains("LEFT JOIN (")
          .contains("CROSS JOIN (SELECT arrayJoin(range(3)) AS number) AS step_num")
          .contains("greatest((SELECT countIf(lvl >= 1) FROM lvl_f0), 1)");
    }
  }

  @Nested
  class BuildInsertSqlChain {

    @Test
    void shouldReturnEmptyStringForNullSteps() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().stepsJson(null).build());
      assertThat(sql).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForEmptyStepsArray() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().stepsJson("[]").build());
      assertThat(sql).isEmpty();
    }

    @Test
    void shouldContainInsertIntoFunnelResultsWithAllColumns() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains(
          "INSERT INTO otel.funnel_results\n"
              + "  (FunnelId, ProjectId, RunTime, StepIndex, StepName, UserCount, ConversionPct, MedianStepSeconds)");
    }

    @Test
    void shouldUseMaterializedUserIdForUniqueUsersMode() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().mode("UNIQUE_USERS").build());
      assertThat(sql)
          .contains("SELECT UserId AS uid")
          .doesNotContain("LogAttributes['user.id']");
    }

    @Test
    void shouldUseMaterializedSessionIdForSessionsMode() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().mode("SESSIONS").build());
      assertThat(sql)
          .contains("SELECT SessionId AS uid")
          .doesNotContain("LogAttributes['session.id']");
    }

    @Test
    void shouldFilterByProjectIdAndPulseType() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql)
          .contains("ResourceAttributes['project.id'] = '" + PROJECT_ID + "'")
          .contains("LogAttributes['pulse.type'] = 'custom_event'");
    }

    @Test
    void shouldNarrowStepEventsToFunnelStepNames() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains(
          "AND Body IN ('screen_view', 'add_to_cart', 'purchase')");
    }

    @Test
    void shouldEmitAttemptsCteAnchoredOnStepZero() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains(
          "attempts AS (\n"
              + "    SELECT uid, FunnelTs AS t0\n"
              + "    FROM step_events\n"
              + "    WHERE Body = 'screen_view'");
    }

    @Test
    void shouldEmitChainCteForEachStepAfterZero() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql)
          .contains("s1 AS (")
          .contains("s2 AS (")
          .doesNotContain("s3 AS (");
    }

    @Test
    void shouldChainStepEventsWithMinAndWindowBound() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().windowSeconds(3600L).build());
      assertThat(sql)
          .contains("min(e.FunnelTs) AS t1")
          .contains("AND e.Body = 'add_to_cart'")
          .contains("AND e.FunnelTs >= a.t0")
          .contains("AND e.FunnelTs <= a.t0 + INTERVAL 3600 SECOND");
    }

    @Test
    void shouldChainStepTwoAgainstPreviousStepTimestamp() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().windowSeconds(7200L).build());
      assertThat(sql)
          .contains("min(e.FunnelTs) AS t2")
          .contains("AND e.Body = 'purchase'")
          .contains("AND e.FunnelTs >= s1.t1")
          .contains("AND e.FunnelTs <= s1.t0 + INTERVAL 7200 SECOND");
    }

    @Test
    void shouldScoreDepthInDescendingStepOrder() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains(
          "multiIf(\n"
              + "             t2 IS NOT NULL, 3,\n"
              + "             t1 IS NOT NULL, 2,\n"
              + "             1\n"
              + "           ) AS depth");
    }

    @Test
    void shouldSelectWinnerWithArgMaxDepthEarliestT0() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains(
          "argMax(tuple(t0, t1, t2), tuple(depth, -toInt64(toUnixTimestamp(t0)))) AS chain");
    }

    @Test
    void shouldEmitOneSelectPerStepWithZeroBasedStepIndex() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql)
          .contains("toUInt8(0), 'screen_view'")
          .contains("toUInt8(1), 'add_to_cart'")
          .contains("toUInt8(2), 'purchase'");
    }

    @Test
    void shouldEmitNullMedianForFirstStep() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains(
          "toUInt8(0), 'screen_view',\n"
              + "       countIf(winning_depth >= 1),\n"
              + "       countIf(winning_depth >= 1) * 100.0 / greatest(count(), 1),\n"
              + "       CAST(NULL AS Nullable(Int64))");
    }

    @Test
    void shouldEmitQuantileMedianForSubsequentSteps() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql)
          .contains("dateDiff('second', chain.1, chain.2)")
          .contains("dateDiff('second', chain.2, chain.3)")
          .contains("accurateCastOrNull(round(quantileTDigest(0.5)")
          .contains("'Int64')");
    }

    @Test
    void shouldUseUnionAllBetweenStepSelects() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql.split("UNION ALL", -1)).hasSize(3);
    }

    @Test
    void shouldHandleSingleStepFunnelWithoutChainCtes() {
      String singleStep = "[{\"eventName\":\"app_open\"}]";
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().stepsJson(singleStep).build());
      assertThat(sql)
          .contains("INSERT INTO otel.funnel_results")
          .contains("attempts AS (")
          .doesNotContain("s1 AS (")
          .contains(", 1 AS depth")
          .contains("argMax(tuple(t0),")
          .contains("toUInt8(0), 'app_open'")
          .doesNotContain("UNION ALL");
    }

    @Test
    void shouldHandleTwoStepFunnel() {
      String twoSteps = "[{\"eventName\":\"view\"},{\"eventName\":\"buy\"}]";
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().stepsJson(twoSteps).build());
      assertThat(sql)
          .contains("s1 AS (")
          .doesNotContain("s2 AS (")
          .contains("multiIf(\n             t1 IS NOT NULL, 2,\n             1")
          .contains("argMax(tuple(t0, t1)")
          .contains("dateDiff('second', chain.1, chain.2)");
    }

    @Test
    void shouldHandleFiveStepFunnel() {
      String fiveSteps = "[{\"eventName\":\"s0\"},{\"eventName\":\"s1\"},{\"eventName\":\"s2\"},"
          + "{\"eventName\":\"s3\"},{\"eventName\":\"s4\"}]";
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().stepsJson(fiveSteps).build());
      assertThat(sql)
          .contains("s1 AS (")
          .contains("s2 AS (")
          .contains("s3 AS (")
          .contains("s4 AS (")
          .doesNotContain("s5 AS (")
          .contains("argMax(tuple(t0, t1, t2, t3, t4)")
          .contains("dateDiff('second', chain.4, chain.5)");
      assertThat(sql.split("UNION ALL", -1)).hasSize(5);
    }

    @Test
    void shouldCarryForwardAllPriorTimestampsInLaterChainCtes() {
      String fourSteps = "[{\"eventName\":\"a\"},{\"eventName\":\"b\"},{\"eventName\":\"c\"},{\"eventName\":\"d\"}]";
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().stepsJson(fourSteps).build());
      assertThat(sql).contains(
          "s3 AS (\n"
              + "    SELECT s2.uid, s2.t0, s2.t1, s2.t2,\n"
              + "           min(e.FunnelTs) AS t3");
      assertThat(sql).contains(
          "GROUP BY s2.uid, s2.t0, s2.t1, s2.t2");
    }

    @Test
    void shouldUseIntervalForAutoMode() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().funnelType("AUTO").dateRangeDays(14).build());
      assertThat(sql).contains("INTERVAL 14 DAY");
    }

    @Test
    void shouldUseToDateTime64ForOnceMode() {
      Instant start = Instant.parse("2024-03-01T00:00:00Z");
      Instant end   = Instant.parse("2024-03-31T23:59:59Z");
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().funnelType("ONCE").startTime(start).endTime(end).build());
      assertThat(sql)
          .contains("toDateTime64('2024-03-01 00:00:00', 9)")
          .contains("toDateTime64('2024-03-31 23:59:59', 9)");
    }

    @Test
    void shouldAppendGlobalFilterClauses() {
      String filtersJson = "[{\"field\":\"OS_NAME\",\"operator\":\"EQ\",\"value\":[\"Android\"]}]";
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().filtersJson(filtersJson).build());
      assertThat(sql).contains("AND ResourceAttributes['os.name'] = 'Android'");
    }

    @Test
    void shouldEscapeSingleQuotesInEventNames() {
      String stepsJson = "[{\"eventName\":\"O'Brien's Event\"},{\"eventName\":\"checkout\"}]";
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().stepsJson(stepsJson).build());
      assertThat(sql).contains("O\\'Brien\\'s Event");
    }

    @Test
    void shouldIncludeFunnelId() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().id(12345L).build());
      assertThat(sql).contains("toUInt64(12345)");
    }

    @Test
    void shouldCastTimestampToDateTimeInScanCte() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains("toDateTime(Timestamp) AS FunnelTs");
    }
  }
}

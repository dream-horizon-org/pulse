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
      assertThat(sql).contains("ProjectId = '" + PROJECT_ID + "'");
    }

    @Test
    void shouldFilterByPulseType() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().build());
      assertThat(sql).contains("PulseType = 'custom_event'");
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
          .contains("EventName = 'screen_view'")
          .contains("EventName = 'add_to_cart'")
          .contains("EventName = 'purchase'");
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
      assertThat(sql).contains("SELECT UserId AS uid");
    }

    @Test
    void shouldUseSessionIdGroupKeyForSessions() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(
          baseRow().mode("SESSIONS").build());
      assertThat(sql).contains("SELECT SessionId AS uid");
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
      assertThat(sql).contains("AND Platform = 'Android'");
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
          .contains("SELECT UserId,")
          .contains("SessionId,")
          .contains("EventName")
          .contains("PulseType = 'custom_event'");
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
    void shouldUseMaterializedUserIdColumnForUniqueUsersMode() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().mode("UNIQUE_USERS").build());
      assertThat(sql)
          .contains("SELECT UserId AS uid")
          .doesNotContain("SELECT LogAttributes['user.id'] AS uid");
    }

    @Test
    void shouldUseMaterializedSessionIdColumnForSessionsMode() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(
          baseRow().mode("SESSIONS").build());
      assertThat(sql)
          .contains("SELECT SessionId AS uid")
          .doesNotContain("SELECT LogAttributes['session.id'] AS uid");
    }

    @Test
    void shouldFilterByProjectIdAndPulseType() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql)
          .contains("ProjectId = '" + PROJECT_ID + "'")
          .contains("PulseType = 'custom_event'");
    }

    @Test
    void shouldNarrowStepEventsToFunnelStepNames() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains(
          "AND EventName IN ('screen_view', 'add_to_cart', 'purchase')");
    }

    @Test
    void shouldEmitAttemptsCteAnchoredOnStepZero() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().build());
      assertThat(sql).contains(
          "attempts AS (\n"
              + "    SELECT uid, FunnelTs AS t0\n"
              + "    FROM step_events\n"
              + "    WHERE EventName = 'screen_view'");
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
    void shouldChainStepEventsWithMinOrNullIfAndWindowBoundInsideAggregate() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().windowSeconds(3600L).build());
      assertThat(sql)
          .contains(
              "minOrNullIf(e.FunnelTs, e.FunnelTs >= a.t0 AND e.FunnelTs <= a.t0 + INTERVAL 3600 SECOND) AS t1")
          .contains("AND e.EventName = 'add_to_cart'")
          .contains("LEFT JOIN step_events e\n")
          .contains("ON e.uid = a.uid");
    }

    @Test
    void shouldChainStepTwoAgainstPreviousStepTimestamp() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlChain(baseRow().windowSeconds(7200L).build());
      assertThat(sql)
          .contains(
              "minOrNullIf(e.FunnelTs, e.FunnelTs >= s1.t1 AND e.FunnelTs <= s1.t0 + INTERVAL 7200 SECOND) AS t2")
          .contains("AND e.EventName = 'purchase'");
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
          .contains(
              "toFloat64(dateDiff('second', tupleElement(chain, 1), tupleElement(chain, 2)))")
          .contains(
              "toFloat64(dateDiff('second', tupleElement(chain, 2), tupleElement(chain, 3)))")
          .contains("quantileExactIf(0.5)(")
          .contains("accurateCastOrNull(round(quantileExactIf(0.5)(")
          .contains("'Int64')")
          .contains("tupleElement(chain, 1) IS NOT NULL AND tupleElement(chain, 2) IS NOT NULL")
          .contains("tupleElement(chain, 2) >= tupleElement(chain, 1)")
          .contains("tupleElement(chain, 3) >= tupleElement(chain, 2)")
          .contains("winning_depth >= 2")
          .contains("winning_depth >= 3");
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
          .contains("tupleElement(chain, 1), tupleElement(chain, 2)");
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
          .contains("tupleElement(chain, 4), tupleElement(chain, 5)");
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
              + "           minOrNullIf(e.FunnelTs, e.FunnelTs >= s2.t2 AND e.FunnelTs <= s2.t0 + INTERVAL 3600 SECOND) AS t3");
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
      assertThat(sql).contains("AND Platform = 'Android'");
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

  @Nested
  class BuildInsertSqlForDefinition {

    @Test
    void shouldUseOrderedChainBuilderWhenStepOrderTypeIsNotUnordered() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlForDefinition(
          baseRow().stepOrderType("ORDERED").build());
      assertThat(sql)
          .contains("attempts AS (")
          .contains("argMax(tuple(")
          .doesNotContain("window_scores AS (");
    }

    @Test
    void shouldUseUnorderedBuilderWhenStepOrderTypeIsUnordered() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlForDefinition(
          baseRow().stepOrderType("UNORDERED").build());
      assertThat(sql)
          .contains("window_scores AS (")
          .contains("uniqExactIf(")
          .contains("best_per_uid AS (")
          .doesNotContain("argMax(tuple(");
    }

    @Test
    void shouldEmitNullMediansForUnorderedFunnels() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSqlForDefinition(
          baseRow().stepOrderType("UNORDERED").build());
      assertThat(sql)
          .contains("countIf(max_steps >= 1)")
          .contains("countIf(max_steps >= 2)")
          .contains("countIf(max_steps >= 3)")
          .contains("CAST(NULL AS Nullable(Int64))")
          .doesNotContain("quantileExactIf(0.5)");
    }
  }

  @Nested
  class BuildSessionStateInsertSql {

    private static final String RUN_TIME = "toDateTime64('2026-04-23 10:00:00.000', 3, 'UTC')";

    @Test
    void shouldReturnEmptyForUnorderedFunnels() {
      String sql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(
          baseRow().stepOrderType("UNORDERED").build(), RUN_TIME);
      assertThat(sql).isEmpty();
    }

    @Test
    void shouldReturnEmptyForZeroStepFunnels() {
      String sql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(
          baseRow().stepsJson("[]").build(), RUN_TIME);
      assertThat(sql).isEmpty();
    }

    @Test
    void shouldTargetFunnelSessionStateTable() {
      String sql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(
          baseRow().stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql).contains("INSERT INTO otel.funnel_session_state");
    }

    @Test
    void shouldAlwaysGroupBySessionIdRegardlessOfFunnelMode() {
      // UNIQUE_USERS funnel must still emit per-session bridge rows.
      String sql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(
          baseRow().mode("UNIQUE_USERS").stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql)
          .contains("SELECT SessionId AS uid")
          .contains("AND SessionId != ''");
    }

    @Test
    void shouldFilterByProjectAndPulseType() {
      String sql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(
          baseRow().stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql)
          .contains("ProjectId = '" + PROJECT_ID + "'")
          .contains("PulseType = 'custom_event'");
    }

    @Test
    void shouldHydrateDimensionsViaLeftJoinOnOtelLogs() {
      String sql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(
          baseRow().stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql)
          .contains("LEFT JOIN otel.otel_logs l")
          .contains("l.SessionId = a.SessionId")
          .contains("any(l.AppVersion)")
          .contains("any(l.TraceId)")
          .contains("any(l.Platform)");
    }

    @Test
    void shouldEmitDropoffStepAsNegativeOneForConverters() {
      String sql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(
          baseRow().stepOrderType("ORDERED").build(), RUN_TIME);
      // stepCount = 3, final index = 2 → converters have LastReachedStep >= 2 → DropoffStep = -1
      assertThat(sql).contains("a.LastReachedStep >= 2, -1, a.LastReachedStep + 1");
    }

    @Test
    void shouldStampEveryRowWithSharedRunTime() {
      String sql = ClickHouseFunnelComputeDao.buildSessionStateInsertSql(
          baseRow().stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql).contains(RUN_TIME);
    }
  }

  @Nested
  class BuildUserStateInsertSql {

    private static final String RUN_TIME = "toDateTime64('2026-04-23 10:00:00.000', 3, 'UTC')";

    @Test
    void shouldReturnEmptyForSessionsMode() {
      String sql = ClickHouseFunnelComputeDao.buildUserStateInsertSql(
          baseRow().mode("SESSIONS").stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnorderedFunnels() {
      String sql = ClickHouseFunnelComputeDao.buildUserStateInsertSql(
          baseRow().mode("UNIQUE_USERS").stepOrderType("UNORDERED").build(), RUN_TIME);
      assertThat(sql).isEmpty();
    }

    @Test
    void shouldTargetFunnelUserStateTable() {
      String sql = ClickHouseFunnelComputeDao.buildUserStateInsertSql(
          baseRow().mode("UNIQUE_USERS").stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql).contains("INSERT INTO otel.funnel_user_state");
    }

    @Test
    void shouldReadFromFunnelSessionStateScopedToSameRun() {
      String sql = ClickHouseFunnelComputeDao.buildUserStateInsertSql(
          baseRow().mode("UNIQUE_USERS").stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql)
          .contains("FROM otel.funnel_session_state")
          .contains("ProjectId = '" + PROJECT_ID + "'")
          .contains("FunnelId = 42")
          .contains("RunTime = " + RUN_TIME)
          .contains("UserId != ''");
    }

    @Test
    void shouldPickCanonicalSessionByFurthestStepAndLatestTimestamp() {
      String sql = ClickHouseFunnelComputeDao.buildUserStateInsertSql(
          baseRow().mode("UNIQUE_USERS").stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql)
          .contains("argMax(SessionId, (LastReachedStep, LastReachedAt)) AS CanonicalSessionId")
          .contains("argMax(LastReachedAt, (LastReachedStep, LastReachedAt))");
    }

    @Test
    void shouldSetDropoffStepToNegativeOneWhenAnySessionConverted() {
      String sql = ClickHouseFunnelComputeDao.buildUserStateInsertSql(
          baseRow().mode("UNIQUE_USERS").stepOrderType("ORDERED").build(), RUN_TIME);
      assertThat(sql).contains("if(sum(DropoffStep = -1) > 0, -1, max(LastReachedStep) + 1)");
    }
  }
}

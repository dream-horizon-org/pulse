package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClickHouseJourneyComputeDaoTest {

  private static final String PROJECT_ID = "proj-xyz";
  private static final String ANCHOR = "checkout_start";

  private JourneyRow.JourneyRowBuilder baseRow() {
    return JourneyRow.builder()
        .id(10L)
        .projectId(PROJECT_ID)
        .name("Test Journey")
        .anchorEvent(ANCHOR)
        .depth(3)
        .direction("START")
        .journeyType("AUTO")
        .mode("UNIQUE_USERS")
        .dateRangeDays(14)
        .filtersJson(null);
    }

  @Nested
  class BuildInsertSql {

    @Test
    void shouldContainInsertIntoJourneyResults() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("INSERT INTO otel.journey_results");
    }

    @Test
    void shouldReadFromOtelLogs() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("FROM otel.otel_logs");
    }

    @Test
    void shouldUsePreWhereForProjectAndTimestamp() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql)
          .contains("PREWHERE ProjectId = '" + PROJECT_ID + "'")
          .contains("AND Timestamp BETWEEN");
    }

    @Test
    void shouldFilterPulseTypeInWhere() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("WHERE PulseType = 'custom_event'");
    }

    @Test
    void shouldEmitArrayWalkCteChain() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql)
          .contains("base AS (")
          .contains("per_uid AS (")
          .contains("walked AS (")
          .contains("arraySort(x -> x.1, groupArray(tuple(ts, EventName))) AS ev");
    }

    @Test
    void shouldUseIndexOfForStartAnchor() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("indexOf(arrayMap(x -> x.2, ev), '" + ANCHOR + "')");
    }

    @Test
    void shouldUseArrayLastIndexForEndAnchor() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "END");
      assertThat(sql).contains("arrayLastIndex(x -> x = '" + ANCHOR + "', arrayMap(x -> x.2, ev))");
    }

    @Test
    void shouldEmitForwardSliceForStart() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().depth(5).build(), "START");
      // depth=5 → arraySlice(ev, anchor_idx, 6)
      assertThat(sql).contains("arraySlice(ev, anchor_idx, 6) AS slice");
    }

    @Test
    void shouldEmitBackwardSliceForEnd() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().depth(5).build(), "END");
      assertThat(sql)
          .contains("greatest(1, anchor_idx - 5)")
          .contains("least(anchor_idx, 6)");
    }

    @Test
    void shouldDropUidsWithoutAnchor() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("WHERE anchor_idx > 0");
    }

    @Test
    void shouldEmitEntryRowWithAnchorEvent() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      // ENTRY row: PosFrom=-1, EventFrom='', PosTo=0, EventTo=anchorEvent, count() FROM walked
      assertThat(sql)
          .contains("toInt32(-1), '', toInt32(0), '" + ANCHOR + "'")
          .contains("toUInt64(count())\nFROM walked");
    }

    @Test
    void shouldEmitEdgesViaArrayJoinNotSelfJoin() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql)
          .contains("arrayJoin(")
          .contains("arrayMap(")
          .contains("range(1, length(slice))")
          .doesNotContain("r2.pos = r1.pos + 1");
    }

    @Test
    void shouldUseUniqExactNotCountDistinct() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("uniqExact(gid)").doesNotContain("count(DISTINCT gid)");
    }

    @Test
    void shouldNotUseRowNumberWindowFunction() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).doesNotContain("row_number()").doesNotContain("PARTITION BY");
    }

    @Test
    void shouldEmitForwardEdgeTupleForStart() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      // i - 1 .. i for forward positions starting at 0 (anchor)
      assertThat(sql)
          .contains("i - 1, tupleElement(slice[i],     2)")
          .contains("i,     tupleElement(slice[i + 1], 2)");
    }

    @Test
    void shouldEmitBackwardEdgeTupleForEnd() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "END");
      assertThat(sql)
          .contains("i      - length(slice), tupleElement(slice[i],     2)")
          .contains("i + 1  - length(slice), tupleElement(slice[i + 1], 2)");
    }

    @Test
    void shouldUseIntervalExprForAutoMode() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(
          baseRow().journeyType("AUTO").dateRangeDays(14).build(), "START");
      assertThat(sql).contains("INTERVAL 14 DAY");
    }

    @Test
    void shouldUseToDateTime64ForOnceMode() {
      Instant start = Instant.parse("2024-05-01T00:00:00Z");
      Instant end   = Instant.parse("2024-05-31T23:59:59Z");
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(
          baseRow().journeyType("ONCE").startTime(start).endTime(end).build(), "START");
      assertThat(sql)
          .contains("toDateTime64('2024-05-01 00:00:00', 9)")
          .contains("toDateTime64('2024-05-31 23:59:59', 9)");
    }

    @Test
    void shouldUseAppInstallationIdGroupKeyForUniqueUsers() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(
          baseRow().mode("UNIQUE_USERS").build(), "START");
      assertThat(sql).contains("SELECT AppInstallationId AS gid");
    }

    @Test
    void shouldUseSessionIdGroupKeyForSessions() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(
          baseRow().mode("SESSIONS").build(), "START");
      assertThat(sql).contains("SELECT SessionId AS gid");
    }

    @Test
    void shouldEscapeSingleQuotesInAnchorEvent() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(
          baseRow().anchorEvent("O'Brien's Checkout").build(), "START");
      assertThat(sql).contains("O\\'Brien\\'s Checkout");
    }

    @Test
    void shouldAppendGlobalFilterClauses() {
      String filtersJson = "[{\"field\":\"OS_NAME\",\"operator\":\"EQ\",\"value\":[\"Android\"]}]";
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(
          baseRow().filtersJson(filtersJson).build(), "START");
      assertThat(sql).contains("AND Platform = 'Android'");
    }

    @Test
    void shouldIncludeJourneyId() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().id(99L).build(), "START");
      assertThat(sql).contains("toUInt64(99)");
    }

    @Test
    void shouldIncludeDirectionLiteral() {
      String startSql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      String endSql   = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "END");
      assertThat(startSql).contains("'START'");
      assertThat(endSql).contains("'END'");
    }

    @Test
    void shouldStampSingleRunTimeLiteralAcrossUnion() {
      // toDateTime64('YYYY-MM-DD …', 3, 'UTC') must appear at least twice (entry + edges)
      // and every occurrence in this query must be the SAME literal — so the latest-run
      // reader (RunTime = max(RunTime)) sees both branches as one run.
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      long count = sql.lines().filter(l -> l.contains("toDateTime64(")
          && l.contains(", 3, 'UTC')")).count();
      assertThat(count).isGreaterThanOrEqualTo(2);
    }
  }

  @Nested
  class BuildInsertSqlLegacy {

    @Test
    void shouldStillContainCteChainForFallback() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSqlLegacy(baseRow().build(), "START");
      assertThat(sql)
          .contains("sessions AS (")
          .contains("positioned AS (")
          .contains("anchor_pos AS (")
          .contains("relative AS (")
          .contains("edges AS (");
    }

    @Test
    void shouldUseAscOrderForStartDirection() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSqlLegacy(baseRow().build(), "START");
      assertThat(sql).contains("ORDER BY l.Timestamp ASC");
    }

    @Test
    void shouldUseDescOrderForEndDirection() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSqlLegacy(baseRow().build(), "END");
      assertThat(sql).contains("ORDER BY l.Timestamp DESC");
    }
  }

  @Nested
  class BuildBatchInsertSql {

    @Test
    void shouldReturnEmptyStringForEmptyList() {
      assertThat(ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(), "START")).isEmpty();
    }

    @Test
    void shouldContainInsertIntoJourneyResults() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "START");
      assertThat(sql).contains("INSERT INTO otel.journey_results");
    }

    @Test
    void shouldEmitSharedBaseScanWithPreWhere() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "START");
      assertThat(sql)
          .contains("base AS (")
          .contains("FROM otel.otel_logs")
          .contains("PREWHERE ProjectId = '" + PROJECT_ID + "'")
          .contains("WHERE PulseType = 'custom_event'");
    }

    @Test
    void shouldEmitOnePerUidAndWalkedCtePerJourney() {
      List<JourneyRow> defs = List.of(
          baseRow().id(1L).build(),
          baseRow().id(2L).build()
      );
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START");
      assertThat(sql)
          .contains("per_uid_j0").contains("walked_j0")
          .contains("per_uid_j1").contains("walked_j1");
    }

    @Test
    void shouldUseMaxDaysAcrossAllJourneysForSharedScan() {
      List<JourneyRow> defs = List.of(
          baseRow().id(1L).dateRangeDays(7).build(),
          baseRow().id(2L).dateRangeDays(30).build()
      );
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START");
      assertThat(sql).contains("INTERVAL 30 DAY");
    }

    @Test
    void shouldAddTighterTimestampFilterForShorterJourney() {
      List<JourneyRow> defs = List.of(
          baseRow().id(1L).dateRangeDays(30).build(),
          baseRow().id(2L).dateRangeDays(7).build()
      );
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START");
      assertThat(sql).contains("ts >= now() - INTERVAL 7 DAY");
    }

    @Test
    void shouldUnionAllJourneyOutputs() {
      List<JourneyRow> defs = List.of(
          baseRow().id(1L).build(),
          baseRow().id(2L).build()
      );
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START");
      assertThat(sql).contains("UNION ALL");
    }

    @Test
    void shouldUseUniqExactInBatch() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "START");
      assertThat(sql).contains("uniqExact(gid)").doesNotContain("count(DISTINCT");
    }

    @Test
    void shouldNotUseWindowFunctionInBatch() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "START");
      assertThat(sql).doesNotContain("row_number()").doesNotContain("PARTITION BY");
    }

    @Test
    void shouldUseArrayLastIndexForEndDirectionInBatch() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "END");
      assertThat(sql).contains("arrayLastIndex(x -> x = '" + ANCHOR + "'");
    }

    @Test
    void shouldUseSessionIdInBatchForSessionJourneys() {
      JourneyRow def = baseRow().mode("SESSIONS").build();
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(def), "START");
      assertThat(sql).contains("SessionId AS gid");
    }
  }
}

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
    void shouldReadFromOtelLogsInSessionsCte() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("FROM otel.otel_logs");
    }

    @Test
    void shouldFilterByProjectId() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("ResourceAttributes['project.id'] = '" + PROJECT_ID + "'");
    }

    @Test
    void shouldFilterByPulseType() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("LogAttributes['pulse.type'] = 'custom_event'");
    }

    @Test
    void shouldIncludeAnchorEventInSessionsCte() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("Body = '" + ANCHOR + "'");
    }

    @Test
    void shouldUseAscOrderForStartDirection() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("ORDER BY l.Timestamp ASC");
    }

    @Test
    void shouldUseDescOrderForEndDirection() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "END");
      assertThat(sql).contains("ORDER BY l.Timestamp DESC");
    }

    @Test
    void shouldUsePlusDirSignForStart() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      // dirSign = +1 appears as `* 1` in the relative CTE
      assertThat(sql).contains("* 1 AS pos");
    }

    @Test
    void shouldUseMinusDirSignForEnd() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "END");
      // dirSign = -1 appears as `* -1` in the relative CTE
      assertThat(sql).contains("* -1 AS pos");
    }

    @Test
    void shouldIncludeSessionsCteChain() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql)
          .contains("sessions AS (")
          .contains("positioned AS (")
          .contains("anchor_pos AS (")
          .contains("relative AS (")
          .contains("edges AS (");
    }

    @Test
    void shouldIncludeEntryRowWithAnchorEvent() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      // ENTRY row: PosFrom=-1, EventFrom='', PosTo=0, EventTo=anchorEvent
      assertThat(sql).contains("'" + ANCHOR + "', count(DISTINCT gid) FROM anchor_pos");
    }

    @Test
    void shouldIncludeEdgeRowsWithUnionAll() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      assertThat(sql).contains("UNION ALL");
      assertThat(sql).contains("FROM edges");
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
    void shouldUseUserIdGroupKeyForUniqueUsers() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(
          baseRow().mode("UNIQUE_USERS").build(), "START");
      assertThat(sql).contains("SELECT DISTINCT UserId AS gid");
    }

    @Test
    void shouldUseSessionIdGroupKeyForSessions() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(
          baseRow().mode("SESSIONS").build(), "START");
      assertThat(sql).contains("SELECT DISTINCT SessionId AS gid");
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
      assertThat(sql).contains("AND ResourceAttributes['os.name'] = 'Android'");
    }

    @Test
    void shouldIncludeDepthInRelativeCte() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().depth(5).build(), "START");
      assertThat(sql).contains("<= 5");
    }

    @Test
    void shouldIncludeJourneyId() {
      String sql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().id(99L).build(), "START");
      assertThat(sql).contains("99");
    }

    @Test
    void shouldIncludeDirection() {
      String startSql = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "START");
      String endSql   = ClickHouseJourneyComputeDao.buildInsertSql(baseRow().build(), "END");
      assertThat(startSql).contains("'START'");
      assertThat(endSql).contains("'END'");
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
    void shouldContainSharedRawCte() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "START");
      assertThat(sql)
          .contains("raw AS (")
          .contains("FROM otel.otel_logs")
          .contains("SELECT UserId,")
          .contains("SessionId,")
          .contains("LogAttributes['pulse.type'] = 'custom_event'")
          .contains("ResourceAttributes,")
          .contains("\n                   LogAttributes\n");
    }

    @Test
    void shouldExposeAttributeMapsInRawCteSoGlobalFiltersResolve() {
      String filtersJson =
          "[{\"field\":\"APP_BUILD_NAME\",\"operator\":\"EQ\",\"value\":[\"9.7.0\"]}]";
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(
          List.of(baseRow().filtersJson(filtersJson).build()), "START");
      assertThat(sql).contains("AND ResourceAttributes['app.build_name'] = '9.7.0'");
    }

    @Test
    void shouldUseMaxDaysAcrossAllJourneys() {
      List<JourneyRow> defs = List.of(
          baseRow().id(1L).dateRangeDays(7).build(),
          baseRow().id(2L).dateRangeDays(30).build()
      );
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START");
      assertThat(sql).contains("INTERVAL 30 DAY");
    }

    @Test
    void shouldCreateOneCteChainPerJourney() {
      List<JourneyRow> defs = List.of(
          baseRow().id(1L).build(),
          baseRow().id(2L).build()
      );
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START");
      assertThat(sql)
          .contains("sess_j0").contains("pos_j0").contains("anc_j0").contains("rel_j0").contains("edg_j0")
          .contains("sess_j1").contains("pos_j1").contains("anc_j1").contains("rel_j1").contains("edg_j1");
    }

    @Test
    void shouldContainUnionAllForMultipleJourneys() {
      List<JourneyRow> defs = List.of(
          baseRow().id(1L).build(),
          baseRow().id(2L).build()
      );
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START");
      assertThat(sql).contains("UNION ALL");
    }

    @Test
    void shouldUseAscOrderForStartDirectionInBatch() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "START");
      assertThat(sql).contains("ORDER BY r.Timestamp ASC");
    }

    @Test
    void shouldUseDescOrderForEndDirectionInBatch() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "END");
      assertThat(sql).contains("ORDER BY r.Timestamp DESC");
    }

    @Test
    void shouldReferenceRawCteNotDirectlyOtelLogsForJourneyCtes() {
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(baseRow().build()), "START");
      // sessions CTE should SELECT FROM raw, not FROM otel.otel_logs
      assertThat(sql).contains("FROM raw WHERE Body");
    }

    @Test
    void shouldAddTighterTimestampFilterForShorterJourney() {
      List<JourneyRow> defs = List.of(
          baseRow().id(1L).dateRangeDays(30).build(),
          baseRow().id(2L).dateRangeDays(7).build()
      );
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(defs, "START");
      assertThat(sql).contains("INTERVAL 7 DAY");
    }

    @Test
    void shouldUseSessionIdInBatchForSessionJourneys() {
      JourneyRow def = baseRow().mode("SESSIONS").build();
      String sql = ClickHouseJourneyComputeDao.buildBatchInsertSql(List.of(def), "START");
      assertThat(sql).contains("SessionId");
    }
  }
}

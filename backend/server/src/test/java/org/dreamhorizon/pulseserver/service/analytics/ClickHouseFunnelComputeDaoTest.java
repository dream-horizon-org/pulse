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
        .funnelType("UNIQUE_USERS")
        .mode("AUTO")
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
          baseRow().funnelType("UNIQUE_USERS").build());
      assertThat(sql).contains("LogAttributes['user.id']");
    }

    @Test
    void shouldUseSessionIdGroupKeyForSessions() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(
          baseRow().funnelType("SESSIONS").build());
      assertThat(sql).contains("LogAttributes['session.id']");
    }

    @Test
    void shouldUseIntervalForAutoMode() {
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(baseRow().mode("AUTO").dateRangeDays(14).build());
      assertThat(sql).contains("INTERVAL 14 DAY");
    }

    @Test
    void shouldUseToDateTime64ForOnceMode() {
      Instant start = Instant.parse("2024-03-01T00:00:00Z");
      Instant end   = Instant.parse("2024-03-31T23:59:59Z");
      String sql = ClickHouseFunnelComputeDao.buildInsertSql(
          baseRow().mode("ONCE").startTime(start).endTime(end).build());
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
      FunnelDefinitionRow def = baseRow().funnelType("SESSIONS").build();
      String sql = ClickHouseFunnelComputeDao.buildBatchInsertSql(List.of(def));
      assertThat(sql).contains("SessionId");
    }
  }
}

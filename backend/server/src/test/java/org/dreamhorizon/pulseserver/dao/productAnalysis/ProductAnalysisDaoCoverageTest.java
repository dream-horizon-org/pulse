package org.dreamhorizon.pulseserver.dao.productAnalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobEntity;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobStatus;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models.EventCatalogEventNameRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.eventcatalog.models.EventCatalogFilterKeyRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionListParams;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagEntityType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelConversionSummaryRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelResultRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyListParams;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.models.JourneyResultRow;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises Lombok-generated builders/getters and enum values for DAO-layer product-analysis models.
 */
class ProductAnalysisDaoCoverageTest {

  @Nested
  class AnalyticsJobStatusEnum {

    @Test
    void shouldHaveAllValues() {
      assertThat(AnalyticsJobStatus.values()).containsExactly(
          AnalyticsJobStatus.PENDING, AnalyticsJobStatus.SUBMITTED, AnalyticsJobStatus.RUNNING,
          AnalyticsJobStatus.SUCCEEDED, AnalyticsJobStatus.FAILED);
    }

    @Test
    void shouldValueOf() {
      assertThat(AnalyticsJobStatus.valueOf("PENDING")).isEqualTo(AnalyticsJobStatus.PENDING);
      assertThat(AnalyticsJobStatus.valueOf("FAILED")).isEqualTo(AnalyticsJobStatus.FAILED);
    }
  }

  @Nested
  class AnalyticsJobTypeEnum {

    @Test
    void shouldHaveAllValues() {
      assertThat(AnalyticsJobType.values()).hasSize(5);
    }

    @Test
    void shouldReturnJobNamePrefixes() {
      assertThat(AnalyticsJobType.FUNNELS_DAILY.getJobNamePrefix()).isEqualTo("funnels-daily-batch");
      assertThat(AnalyticsJobType.JOURNEYS_DAILY.getJobNamePrefix()).isEqualTo("journeys-daily-batch");
      assertThat(AnalyticsJobType.EVENTS_INCREMENTAL.getJobNamePrefix()).isEqualTo("events-incremental-batch");
      assertThat(AnalyticsJobType.FUNNEL.getJobNamePrefix()).isEqualTo("funnel-onsave-");
      assertThat(AnalyticsJobType.JOURNEY.getJobNamePrefix()).isEqualTo("journey-onsave-");
    }

    @Test
    void shouldValueOf() {
      assertThat(AnalyticsJobType.valueOf("FUNNEL")).isEqualTo(AnalyticsJobType.FUNNEL);
    }
  }

  @Nested
  class FunnelJourneyTagEntityTypeEnum {

    @Test
    void shouldHaveAllValues() {
      assertThat(FunnelJourneyTagEntityType.values()).containsExactly(
          FunnelJourneyTagEntityType.FUNNEL, FunnelJourneyTagEntityType.JOURNEY);
    }

    @Test
    void shouldValueOf() {
      assertThat(FunnelJourneyTagEntityType.valueOf("FUNNEL")).isEqualTo(FunnelJourneyTagEntityType.FUNNEL);
    }
  }

  @Nested
  class AnalyticsJobEntityModel {

    @Test
    void shouldBuildAndReadAllFields() {
      LocalDateTime now = LocalDateTime.now();
      AnalyticsJobEntity entity = AnalyticsJobEntity.builder()
          .id(1L)
          .jobType(AnalyticsJobType.FUNNEL)
          .referenceId(42L)
          .jobId("job-abc")
          .status(AnalyticsJobStatus.RUNNING)
          .errorMessage("err")
          .startedAt(now)
          .completedAt(now)
          .createdAt(now)
          .build();

      assertThat(entity.getId()).isEqualTo(1L);
      assertThat(entity.getJobType()).isEqualTo(AnalyticsJobType.FUNNEL);
      assertThat(entity.getReferenceId()).isEqualTo(42L);
      assertThat(entity.getJobId()).isEqualTo("job-abc");
      assertThat(entity.getStatus()).isEqualTo(AnalyticsJobStatus.RUNNING);
      assertThat(entity.getErrorMessage()).isEqualTo("err");
      assertThat(entity.getStartedAt()).isEqualTo(now);
      assertThat(entity.getCompletedAt()).isEqualTo(now);
      assertThat(entity.getCreatedAt()).isEqualTo(now);
      assertThat(entity.toString()).isNotNull();
    }

    @Test
    void shouldSupportNoArgsAndSetters() {
      AnalyticsJobEntity entity = new AnalyticsJobEntity();
      entity.setId(10L);
      entity.setJobId("j");
      entity.setStatus(AnalyticsJobStatus.SUCCEEDED);
      assertThat(entity.getId()).isEqualTo(10L);
      assertThat(entity.getJobId()).isEqualTo("j");
      assertThat(entity.getStatus()).isEqualTo(AnalyticsJobStatus.SUCCEEDED);
    }

    @Test
    void shouldSupportAllArgsConstructor() {
      LocalDateTime t = LocalDateTime.now();
      AnalyticsJobEntity entity = new AnalyticsJobEntity(
          1L, AnalyticsJobType.JOURNEY, 2L, "id", AnalyticsJobStatus.PENDING, null, t, t, t);
      AnalyticsJobEntity same = new AnalyticsJobEntity(
          1L, AnalyticsJobType.JOURNEY, 2L, "id", AnalyticsJobStatus.PENDING, null, t, t, t);
      assertThat(entity).isEqualTo(same).hasSameHashCodeAs(same);
    }
  }

  @Nested
  class EventCatalogEventNameRowModel {

    @Test
    void shouldBuildAndReadFields() {
      EventCatalogEventNameRow row = EventCatalogEventNameRow.builder().name("CLICK").build();
      assertThat(row.getName()).isEqualTo("CLICK");
      assertThat(row.toString()).contains("CLICK");
    }

    @Test
    void shouldSupportNoArgsSettersAndEquality() {
      EventCatalogEventNameRow a = new EventCatalogEventNameRow();
      a.setName("VIEW");
      EventCatalogEventNameRow b = new EventCatalogEventNameRow("VIEW");
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
      assertThat(a.getName()).isEqualTo("VIEW");
    }
  }

  @Nested
  class EventCatalogFilterKeyRowModel {

    @Test
    void shouldBuildAndReadFields() {
      EventCatalogFilterKeyRow row = EventCatalogFilterKeyRow.builder().filterKey("country").build();
      assertThat(row.getFilterKey()).isEqualTo("country");
      assertThat(row.toString()).contains("country");
    }

    @Test
    void shouldSupportNoArgsAndAllArgsConstructor() {
      EventCatalogFilterKeyRow a = new EventCatalogFilterKeyRow();
      a.setFilterKey("city");
      EventCatalogFilterKeyRow b = new EventCatalogFilterKeyRow("city");
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
  }

  @Nested
  class FunnelConversionSummaryRowModel {

    @Test
    void shouldBuildAndReadAllFields() {
      FunnelConversionSummaryRow row = FunnelConversionSummaryRow.builder()
          .funnelId(1L)
          .conversionPct(12.5)
          .conversionTrend(-1.2)
          .build();

      assertThat(row.getFunnelId()).isEqualTo(1L);
      assertThat(row.getConversionPct()).isEqualTo(12.5);
      assertThat(row.getConversionTrend()).isEqualTo(-1.2);
      assertThat(row.toString()).isNotNull();
    }

    @Test
    void shouldSupportConstructors() {
      FunnelConversionSummaryRow a = new FunnelConversionSummaryRow();
      a.setFunnelId(5L);
      FunnelConversionSummaryRow b = new FunnelConversionSummaryRow(5L, null, null);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
  }

  @Nested
  class FunnelResultRowModel {

    @Test
    void shouldBuildAndReadAllFields() {
      FunnelResultRow row = FunnelResultRow.builder()
          .stepIndex(0)
          .stepName("signup")
          .userCount(100L)
          .conversionPct(90.0)
          .medianStepSeconds(10L)
          .build();

      assertThat(row.getStepIndex()).isEqualTo(0);
      assertThat(row.getStepName()).isEqualTo("signup");
      assertThat(row.getUserCount()).isEqualTo(100L);
      assertThat(row.getConversionPct()).isEqualTo(90.0);
      assertThat(row.getMedianStepSeconds()).isEqualTo(10L);
      assertThat(row.toString()).contains("signup");
    }

    @Test
    void shouldSupportNoArgsAndAllArgsAndEquality() {
      Instant runTime = Instant.parse("2026-01-01T12:00:00Z");
      FunnelResultRow a = new FunnelResultRow();
      a.setStepIndex(1);
      a.setStepName("checkout");
      a.setUserCount(50L);
      a.setConversionPct(50.0);
      a.setMedianStepSeconds(20L);
      a.setRunTime(runTime);
      FunnelResultRow b = new FunnelResultRow(1, "checkout", 50L, 50.0, 20L, runTime);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
  }

  @Nested
  class FunnelDefinitionRowModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      FunnelDefinitionRow row = FunnelDefinitionRow.builder()
          .id(1L)
          .projectId("p-1")
          .name("Onboarding")
          .description("d")
          .funnelType("AUTO")
          .stepOrderType("ORDERED")
          .stepsJson("[]")
          .windowSeconds(86400L)
          .mode("UNIQUE_USERS")
          .filtersJson("[]")
          .dateRangeDays(7)
          .startTime(now)
          .endTime(now)
          .expiry(now)
          .createdAt(now)
          .updatedAt(now)
          .createdBy("u-1")
          .latestJobStatus("SUCCEEDED")
          .totalCount(100L)
          .build();

      assertThat(row.getId()).isEqualTo(1L);
      assertThat(row.getProjectId()).isEqualTo("p-1");
      assertThat(row.getName()).isEqualTo("Onboarding");
      assertThat(row.getDescription()).isEqualTo("d");
      assertThat(row.getFunnelType()).isEqualTo("AUTO");
      assertThat(row.getStepOrderType()).isEqualTo("ORDERED");
      assertThat(row.getStepsJson()).isEqualTo("[]");
      assertThat(row.getWindowSeconds()).isEqualTo(86400L);
      assertThat(row.getMode()).isEqualTo("UNIQUE_USERS");
      assertThat(row.getFiltersJson()).isEqualTo("[]");
      assertThat(row.getDateRangeDays()).isEqualTo(7);
      assertThat(row.getStartTime()).isEqualTo(now);
      assertThat(row.getEndTime()).isEqualTo(now);
      assertThat(row.getExpiry()).isEqualTo(now);
      assertThat(row.getCreatedAt()).isEqualTo(now);
      assertThat(row.getUpdatedAt()).isEqualTo(now);
      assertThat(row.getCreatedBy()).isEqualTo("u-1");
      assertThat(row.getLatestJobStatus()).isEqualTo("SUCCEEDED");
      assertThat(row.getTotalCount()).isEqualTo(100L);
      assertThat(row.toString()).isNotNull();
      FunnelDefinitionRow same = FunnelDefinitionRow.builder()
          .id(1L).projectId("p-1").name("Onboarding").description("d")
          .funnelType("AUTO").stepOrderType("ORDERED").stepsJson("[]").windowSeconds(86400L)
          .mode("UNIQUE_USERS").filtersJson("[]").dateRangeDays(7).startTime(now).endTime(now)
          .expiry(now).createdAt(now).updatedAt(now).createdBy("u-1").latestJobStatus("SUCCEEDED")
          .totalCount(100L).build();
      assertThat(row).isEqualTo(same).hasSameHashCodeAs(same);
    }
  }

  @Nested
  class FunnelDefinitionListParamsModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      FunnelDefinitionListParams params = FunnelDefinitionListParams.builder()
          .statuses(List.of("ACTIVE"))
          .funnelType("AUTO")
          .nameLikePrefix("pre")
          .ftsBooleanQuery("+foo")
          .useFullTextSearch(true)
          .updatedAfter(now)
          .updatedBefore(now)
          .createdBy("u-1")
          .limit(10)
          .offset(0)
          .build();

      assertThat(params.getStatuses()).containsExactly("ACTIVE");
      assertThat(params.getFunnelType()).isEqualTo("AUTO");
      assertThat(params.getNameLikePrefix()).isEqualTo("pre");
      assertThat(params.getFtsBooleanQuery()).isEqualTo("+foo");
      assertThat(params.isUseFullTextSearch()).isTrue();
      assertThat(params.getUpdatedAfter()).isEqualTo(now);
      assertThat(params.getUpdatedBefore()).isEqualTo(now);
      assertThat(params.getCreatedBy()).isEqualTo("u-1");
      assertThat(params.getLimit()).isEqualTo(10);
      assertThat(params.getOffset()).isEqualTo(0);
      assertThat(params.toString()).isNotNull();
    }
  }

  @Nested
  class JourneyRowModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      JourneyRow row = JourneyRow.builder()
          .id(1L)
          .projectId("p-1")
          .name("j")
          .description("d")
          .anchorEvent("app_open")
          .direction("START")
          .depth(5)
          .mode("UNIQUE_USERS")
          .filtersJson("[]")
          .startTime(now)
          .endTime(now)
          .journeyType("AUTO")
          .expiry(now)
          .dateRangeDays(7)
          .createdAt(now)
          .updatedAt(now)
          .createdBy("u-1")
          .latestJobStatus("RUNNING")
          .totalCount(3L)
          .build();

      assertThat(row.getId()).isEqualTo(1L);
      assertThat(row.getProjectId()).isEqualTo("p-1");
      assertThat(row.getName()).isEqualTo("j");
      assertThat(row.getDescription()).isEqualTo("d");
      assertThat(row.getAnchorEvent()).isEqualTo("app_open");
      assertThat(row.getDirection()).isEqualTo("START");
      assertThat(row.getDepth()).isEqualTo(5);
      assertThat(row.getMode()).isEqualTo("UNIQUE_USERS");
      assertThat(row.getFiltersJson()).isEqualTo("[]");
      assertThat(row.getStartTime()).isEqualTo(now);
      assertThat(row.getEndTime()).isEqualTo(now);
      assertThat(row.getJourneyType()).isEqualTo("AUTO");
      assertThat(row.getExpiry()).isEqualTo(now);
      assertThat(row.getDateRangeDays()).isEqualTo(7);
      assertThat(row.getCreatedAt()).isEqualTo(now);
      assertThat(row.getUpdatedAt()).isEqualTo(now);
      assertThat(row.getCreatedBy()).isEqualTo("u-1");
      assertThat(row.getLatestJobStatus()).isEqualTo("RUNNING");
      assertThat(row.getTotalCount()).isEqualTo(3L);
      assertThat(row.toString()).isNotNull();
    }
  }

  @Nested
  class JourneyListParamsModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      JourneyListParams params = JourneyListParams.builder()
          .statuses(List.of("ACTIVE", "PAUSED"))
          .journeyType("AUTO")
          .nameLikePrefix("pre")
          .ftsBooleanQuery("+x")
          .useFullTextSearch(false)
          .updatedAfter(now)
          .updatedBefore(now)
          .createdBy("u-1")
          .limit(20)
          .offset(5)
          .build();

      assertThat(params.getStatuses()).containsExactly("ACTIVE", "PAUSED");
      assertThat(params.getJourneyType()).isEqualTo("AUTO");
      assertThat(params.getNameLikePrefix()).isEqualTo("pre");
      assertThat(params.getFtsBooleanQuery()).isEqualTo("+x");
      assertThat(params.isUseFullTextSearch()).isFalse();
      assertThat(params.getUpdatedAfter()).isEqualTo(now);
      assertThat(params.getUpdatedBefore()).isEqualTo(now);
      assertThat(params.getCreatedBy()).isEqualTo("u-1");
      assertThat(params.getLimit()).isEqualTo(20);
      assertThat(params.getOffset()).isEqualTo(5);
      assertThat(params.toString()).isNotNull();
    }
  }

  @Nested
  class JourneyResultRowModel {

    @Test
    void shouldBuildAndReadAllFields() {
      JourneyResultRow row = JourneyResultRow.builder()
          .direction("START")
          .posFrom(0)
          .eventFrom("app_open")
          .posTo(1)
          .eventTo("login")
          .userCount(100L)
          .build();

      assertThat(row.getDirection()).isEqualTo("START");
      assertThat(row.getPosFrom()).isEqualTo(0);
      assertThat(row.getEventFrom()).isEqualTo("app_open");
      assertThat(row.getPosTo()).isEqualTo(1);
      assertThat(row.getEventTo()).isEqualTo("login");
      assertThat(row.getUserCount()).isEqualTo(100L);
      assertThat(row.toString()).isNotNull();
    }

    @Test
    void shouldSupportConstructorsAndEquality() {
      Instant runTime = Instant.parse("2026-01-01T12:00:00Z");
      JourneyResultRow a = new JourneyResultRow();
      a.setDirection("END");
      a.setPosFrom(1);
      a.setEventFrom("logout");
      a.setPosTo(2);
      a.setEventTo("exit");
      a.setUserCount(5L);
      a.setRunTime(runTime);
      JourneyResultRow b =
          new JourneyResultRow("END", 1, "logout", 2, "exit", 5L, runTime);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
  }

  // tiny sanity check to ensure assertThatThrownBy is actually referenced
  @Test
  void shouldImportAssertThatThrownBy() {
    assertThatThrownBy(() -> {
      throw new RuntimeException("x");
    }).isInstanceOf(RuntimeException.class);
  }
}

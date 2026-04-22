package org.dreamhorizon.pulseserver.resources.productAnalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.analysis.AnalysisComputedStatus;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.CreateFunnelDefinitionRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelAttributeFilter;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDefinitionListResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDefinitionResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDefinitionStatus;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDefinitionStep;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelFilterOperator;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelListQueryParams;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelMode;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelResultsResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelStep;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelStepMeasureResult;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelsDto;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.StepOrderType;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.UpdateFunnelDefinitionRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.CreateJourneyRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyDirection;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyListQueryParams;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyListResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyResultsResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneySankeyLink;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneySankeyNode;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.UpdateJourneyRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelEventsResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelFilterKeysResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelFilterValuesResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelJourneyTagsListResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.ListFilterOptions;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.ReplaceEntityTagsRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises Lombok-generated builders/getters/setters and enum parsers for REST-layer
 * product-analysis models.
 */
class ProductAnalysisRestModelCoverageTest {

  @Nested
  class FunnelDefinitionStatusEnum {

    @Test
    void shouldHaveAllValues() {
      assertThat(FunnelDefinitionStatus.values()).containsExactly(
          FunnelDefinitionStatus.ACTIVE, FunnelDefinitionStatus.PAUSED, FunnelDefinitionStatus.ARCHIVED);
    }

    @Test
    void shouldRoundTripJson() {
      assertThat(FunnelDefinitionStatus.ACTIVE.toJson()).isEqualTo("ACTIVE");
      assertThat(FunnelDefinitionStatus.fromJson("active")).isEqualTo(FunnelDefinitionStatus.ACTIVE);
      assertThat(FunnelDefinitionStatus.fromJson("PAUSED")).isEqualTo(FunnelDefinitionStatus.PAUSED);
      assertThat(FunnelDefinitionStatus.fromJson(" archived ")).isEqualTo(FunnelDefinitionStatus.ARCHIVED);
    }

    @Test
    void shouldReturnNullForBlankAndThrowForUnknown() {
      assertThat(FunnelDefinitionStatus.fromJson(null)).isNull();
      assertThat(FunnelDefinitionStatus.fromJson("")).isNull();
      assertThatThrownBy(() -> FunnelDefinitionStatus.fromJson("bogus"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class FunnelFilterOperatorEnum {

    @Test
    void shouldHaveAllValues() {
      assertThat(FunnelFilterOperator.values()).containsExactly(
          FunnelFilterOperator.EQ, FunnelFilterOperator.NE,
          FunnelFilterOperator.IN, FunnelFilterOperator.NOT_IN);
    }

    @Test
    void shouldRoundTripJson() {
      assertThat(FunnelFilterOperator.EQ.toJson()).isEqualTo("EQ");
      assertThat(FunnelFilterOperator.fromJson("eq")).isEqualTo(FunnelFilterOperator.EQ);
      assertThat(FunnelFilterOperator.fromJson("not in")).isEqualTo(FunnelFilterOperator.NOT_IN);
      assertThat(FunnelFilterOperator.fromJson("NOT_IN")).isEqualTo(FunnelFilterOperator.NOT_IN);
    }

    @Test
    void shouldReturnNullForBlankAndThrowForUnknown() {
      assertThat(FunnelFilterOperator.fromJson(null)).isNull();
      assertThat(FunnelFilterOperator.fromJson("   ")).isNull();
      assertThatThrownBy(() -> FunnelFilterOperator.fromJson("bogus"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class FunnelModeEnum {

    @Test
    void shouldHaveAllValues() {
      assertThat(FunnelMode.values()).containsExactly(FunnelMode.UNIQUE_USERS, FunnelMode.SESSIONS);
      assertThat(FunnelMode.valueOf("SESSIONS")).isEqualTo(FunnelMode.SESSIONS);
    }
  }

  @Nested
  class FunnelTypeEnum {

    @Test
    void shouldHaveAllValues() {
      assertThat(FunnelType.values()).containsExactly(FunnelType.AUTO, FunnelType.ONCE);
    }

    @Test
    void shouldRoundTripJson() {
      assertThat(FunnelType.AUTO.toJson()).isEqualTo("AUTO");
      assertThat(FunnelType.fromJson("auto")).isEqualTo(FunnelType.AUTO);
      assertThat(FunnelType.fromJson("ONCE")).isEqualTo(FunnelType.ONCE);
    }

    @Test
    void shouldHandleNullAndUnknown() {
      assertThat(FunnelType.fromJson(null)).isNull();
      assertThat(FunnelType.fromJson("")).isNull();
      assertThatThrownBy(() -> FunnelType.fromJson("x")).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class StepOrderTypeEnum {

    @Test
    void shouldRoundTripJson() {
      assertThat(StepOrderType.values()).containsExactly(StepOrderType.ORDERED, StepOrderType.UNORDERED);
      assertThat(StepOrderType.ORDERED.toJson()).isEqualTo("ORDERED");
      assertThat(StepOrderType.fromJson("ordered")).isEqualTo(StepOrderType.ORDERED);
      assertThat(StepOrderType.fromJson("UNORDERED")).isEqualTo(StepOrderType.UNORDERED);
      assertThat(StepOrderType.fromJson(null)).isNull();
      assertThat(StepOrderType.fromJson("")).isNull();
      assertThatThrownBy(() -> StepOrderType.fromJson("bogus"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class JourneyDirectionEnum {

    @Test
    void shouldRoundTripJson() {
      assertThat(JourneyDirection.values()).containsExactly(JourneyDirection.START, JourneyDirection.END);
      assertThat(JourneyDirection.START.toJson()).isEqualTo("START");
      assertThat(JourneyDirection.fromJson("start")).isEqualTo(JourneyDirection.START);
      assertThat(JourneyDirection.fromJson("END")).isEqualTo(JourneyDirection.END);
      assertThat(JourneyDirection.fromJson(null)).isNull();
      assertThat(JourneyDirection.fromJson("")).isNull();
      assertThatThrownBy(() -> JourneyDirection.fromJson("bogus"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class AnalysisComputedStatusEnum {

    @Test
    void shouldRoundTripJson() {
      assertThat(AnalysisComputedStatus.values()).hasSize(6);
      assertThat(AnalysisComputedStatus.ACTIVE.toJson()).isEqualTo("ACTIVE");
      assertThat(AnalysisComputedStatus.fromJson("active")).isEqualTo(AnalysisComputedStatus.ACTIVE);
      assertThat(AnalysisComputedStatus.fromJson(null)).isNull();
      assertThat(AnalysisComputedStatus.fromJson("")).isNull();
      assertThatThrownBy(() -> AnalysisComputedStatus.fromJson("bogus"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class CreateFunnelDefinitionRequestModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      FunnelDefinitionStep step = FunnelDefinitionStep.builder().eventName("signup").build();
      FunnelAttributeFilter filter = FunnelAttributeFilter.builder()
          .field("country").operator(FunnelFilterOperator.EQ).value(List.of("US")).build();

      CreateFunnelDefinitionRequest req = CreateFunnelDefinitionRequest.builder()
          .name("f")
          .description("d")
          .funnelType(FunnelType.AUTO)
          .stepOrderType(StepOrderType.ORDERED)
          .steps(List.of(step))
          .filters(List.of(filter))
          .windowSeconds(3600L)
          .mode(FunnelMode.UNIQUE_USERS)
          .dateRangeDays(14)
          .startTime(now)
          .endTime(now)
          .expiryDate(now)
          .tags(List.of("t1", "t2"))
          .build();

      assertThat(req.getName()).isEqualTo("f");
      assertThat(req.getDescription()).isEqualTo("d");
      assertThat(req.getFunnelType()).isEqualTo(FunnelType.AUTO);
      assertThat(req.getStepOrderType()).isEqualTo(StepOrderType.ORDERED);
      assertThat(req.getSteps()).containsExactly(step);
      assertThat(req.getFilters()).containsExactly(filter);
      assertThat(req.getWindowSeconds()).isEqualTo(3600L);
      assertThat(req.getMode()).isEqualTo(FunnelMode.UNIQUE_USERS);
      assertThat(req.getDateRangeDays()).isEqualTo(14);
      assertThat(req.getStartTime()).isEqualTo(now);
      assertThat(req.getEndTime()).isEqualTo(now);
      assertThat(req.getExpiryDate()).isEqualTo(now);
      assertThat(req.getTags()).containsExactly("t1", "t2");
      assertThat(req.toString()).isNotNull();
    }

    @Test
    void shouldDefaultValues() {
      CreateFunnelDefinitionRequest req = CreateFunnelDefinitionRequest.builder().name("x").build();
      assertThat(req.getFunnelType()).isEqualTo(FunnelType.AUTO);
      assertThat(req.getStepOrderType()).isEqualTo(StepOrderType.ORDERED);
      assertThat(req.getWindowSeconds()).isEqualTo(86400L);
      assertThat(req.getMode()).isEqualTo(FunnelMode.UNIQUE_USERS);
      assertThat(req.getDateRangeDays()).isEqualTo(7);
    }

    @Test
    void shouldSupportNoArgsAndSetters() {
      CreateFunnelDefinitionRequest r = new CreateFunnelDefinitionRequest();
      r.setName("n");
      assertThat(r.getName()).isEqualTo("n");
    }
  }

  @Nested
  class FunnelAttributeFilterModel {

    @Test
    void shouldBuildAndReadFields() {
      FunnelAttributeFilter f = FunnelAttributeFilter.builder()
          .field("device").operator(FunnelFilterOperator.IN).value(List.of("android", "ios")).build();
      assertThat(f.getField()).isEqualTo("device");
      assertThat(f.getOperator()).isEqualTo(FunnelFilterOperator.IN);
      assertThat(f.getValue()).containsExactly("android", "ios");
      assertThat(f.toString()).contains("device");
      FunnelAttributeFilter same = new FunnelAttributeFilter(
          "device", FunnelFilterOperator.IN, List.of("android", "ios"));
      assertThat(f).isEqualTo(same).hasSameHashCodeAs(same);
    }
  }

  @Nested
  class FunnelDefinitionListResponseModel {

    @Test
    void shouldBuildAndReadAllFields() {
      FunnelDefinitionResponse item = FunnelDefinitionResponse.builder().id(1L).build();
      ListFilterOptions opts = ListFilterOptions.builder().creators(List.of("a")).tags(List.of("t")).build();
      FunnelDefinitionListResponse resp = FunnelDefinitionListResponse.builder()
          .items(List.of(item))
          .totalCount(1L)
          .page(1)
          .pageSize(10)
          .totalPages(1)
          .filterOptions(opts)
          .build();

      assertThat(resp.getItems()).containsExactly(item);
      assertThat(resp.getTotalCount()).isEqualTo(1L);
      assertThat(resp.getPage()).isEqualTo(1);
      assertThat(resp.getPageSize()).isEqualTo(10);
      assertThat(resp.getTotalPages()).isEqualTo(1);
      assertThat(resp.getFilterOptions()).isEqualTo(opts);
      assertThat(resp.toString()).isNotNull();
    }
  }

  @Nested
  class FunnelDefinitionResponseModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      FunnelResultsResponse results = FunnelResultsResponse.builder().overallConversionRate(50.0).build();
      FunnelDefinitionResponse resp = FunnelDefinitionResponse.builder()
          .id(1L)
          .projectId("p")
          .name("n")
          .description("d")
          .status(AnalysisComputedStatus.ACTIVE)
          .funnelType(FunnelType.AUTO)
          .stepOrderType(StepOrderType.ORDERED)
          .steps(List.of())
          .filters(List.of())
          .windowSeconds(60L)
          .mode(FunnelMode.UNIQUE_USERS)
          .dateRangeDays(7)
          .startTime(now)
          .endTime(now)
          .expiry(now)
          .createdAt(now)
          .updatedAt(now)
          .createdBy("u")
          .overallConversionRate(50.0)
          .conversionTrend(2.0)
          .funnelResults(results)
          .tags(List.of("t1"))
          .build();

      assertThat(resp.getId()).isEqualTo(1L);
      assertThat(resp.getProjectId()).isEqualTo("p");
      assertThat(resp.getName()).isEqualTo("n");
      assertThat(resp.getDescription()).isEqualTo("d");
      assertThat(resp.getStatus()).isEqualTo(AnalysisComputedStatus.ACTIVE);
      assertThat(resp.getFunnelType()).isEqualTo(FunnelType.AUTO);
      assertThat(resp.getStepOrderType()).isEqualTo(StepOrderType.ORDERED);
      assertThat(resp.getSteps()).isEmpty();
      assertThat(resp.getFilters()).isEmpty();
      assertThat(resp.getWindowSeconds()).isEqualTo(60L);
      assertThat(resp.getMode()).isEqualTo(FunnelMode.UNIQUE_USERS);
      assertThat(resp.getDateRangeDays()).isEqualTo(7);
      assertThat(resp.getStartTime()).isEqualTo(now);
      assertThat(resp.getEndTime()).isEqualTo(now);
      assertThat(resp.getExpiry()).isEqualTo(now);
      assertThat(resp.getCreatedAt()).isEqualTo(now);
      assertThat(resp.getUpdatedAt()).isEqualTo(now);
      assertThat(resp.getCreatedBy()).isEqualTo("u");
      assertThat(resp.getOverallConversionRate()).isEqualTo(50.0);
      assertThat(resp.getConversionTrend()).isEqualTo(2.0);
      assertThat(resp.getFunnelResults()).isEqualTo(results);
      assertThat(resp.getTags()).containsExactly("t1");
    }
  }

  @Nested
  class FunnelDefinitionStepModel {

    @Test
    void shouldBuildAndReadFields() {
      FunnelDefinitionStep step = FunnelDefinitionStep.builder().eventName("click").build();
      assertThat(step.getEventName()).isEqualTo("click");
      FunnelDefinitionStep same = new FunnelDefinitionStep("click");
      assertThat(step).isEqualTo(same).hasSameHashCodeAs(same);
      assertThat(step.toString()).contains("click");
    }
  }

  @Nested
  class FunnelListQueryParamsModel {

    @Test
    void shouldSetAndReadAllFields() {
      FunnelListQueryParams p = new FunnelListQueryParams();
      p.setStatus(List.of("ACTIVE"));
      p.setFunnelType("AUTO");
      p.setSearch("s");
      p.setSearchMode("like");
      p.setUpdatedAfter("2024-01-01");
      p.setUpdatedBefore("2024-02-01");
      p.setCreatedBy("u");
      p.setPage(1);
      p.setPageSize(10);
      p.setLimit(50);
      p.setOffset(0);

      assertThat(p.getStatus()).containsExactly("ACTIVE");
      assertThat(p.getFunnelType()).isEqualTo("AUTO");
      assertThat(p.getSearch()).isEqualTo("s");
      assertThat(p.getSearchMode()).isEqualTo("like");
      assertThat(p.getUpdatedAfter()).isEqualTo("2024-01-01");
      assertThat(p.getUpdatedBefore()).isEqualTo("2024-02-01");
      assertThat(p.getCreatedBy()).isEqualTo("u");
      assertThat(p.getPage()).isEqualTo(1);
      assertThat(p.getPageSize()).isEqualTo(10);
      assertThat(p.getLimit()).isEqualTo(50);
      assertThat(p.getOffset()).isEqualTo(0);
      assertThat(p.toString()).isNotNull();
    }
  }

  @Nested
  class FunnelResultsResponseModel {

    @Test
    void shouldBuildAndReadFields() {
      FunnelStepMeasureResult step = FunnelStepMeasureResult.builder().stepName("s").count(1L).build();
      FunnelResultsResponse resp = FunnelResultsResponse.builder()
          .steps(List.of(step))
          .totalEnteredUsers(100L)
          .overallConversionRate(25.0)
          .build();

      assertThat(resp.getSteps()).containsExactly(step);
      assertThat(resp.getTotalEnteredUsers()).isEqualTo(100L);
      assertThat(resp.getOverallConversionRate()).isEqualTo(25.0);
      assertThat(resp.toString()).isNotNull();
    }
  }

  @Nested
  class FunnelStepMeasureResultModel {

    @Test
    void shouldBuildAndReadAllFields() {
      FunnelStepMeasureResult r = FunnelStepMeasureResult.builder()
          .stepName("s")
          .count(10L)
          .conversionRate(50.0)
          .dropoffRate(10.0)
          .medianStepSeconds(5L)
          .build();
      assertThat(r.getStepName()).isEqualTo("s");
      assertThat(r.getCount()).isEqualTo(10L);
      assertThat(r.getConversionRate()).isEqualTo(50.0);
      assertThat(r.getDropoffRate()).isEqualTo(10.0);
      assertThat(r.getMedianStepSeconds()).isEqualTo(5L);
      assertThat(r.toString()).isNotNull();
    }
  }

  @Nested
  class FunnelStepModel {

    @Test
    void shouldBuildAndReadFields() {
      FunnelStep.StepFilter sf = new FunnelStep.StepFilter(
          "field", QueryRequest.Operator.EQ, List.of("v"));
      FunnelStep step = FunnelStep.builder()
          .eventName("e")
          .dataType("TRACES")
          .pulseType("p")
          .stepFilters(List.of(sf))
          .build();

      assertThat(step.getEventName()).isEqualTo("e");
      assertThat(step.getDataType()).isEqualTo("TRACES");
      assertThat(step.getPulseType()).isEqualTo("p");
      assertThat(step.getStepFilters()).containsExactly(sf);
      assertThat(sf.getField()).isEqualTo("field");
      assertThat(sf.getOperator()).isEqualTo(QueryRequest.Operator.EQ);
      assertThat(sf.getValue()).containsExactly("v");
      FunnelStep.StepFilter empty = new FunnelStep.StepFilter();
      empty.setField("x");
      assertThat(empty.getField()).isEqualTo("x");
      assertThat(step.toString()).isNotNull();
    }
  }

  @Nested
  class FunnelsDtoModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      FunnelsDto dto = FunnelsDto.builder()
          .id(1L)
          .projectId("p")
          .name("n")
          .description("d")
          .stepsJson(List.of())
          .windowSeconds(60L)
          .mode("UNIQUE_USERS")
          .dateRangeDays(7)
          .filtersJson(List.of())
          .createdAt(now)
          .updatedAt(now)
          .createdBy("u")
          .status("ACTIVE")
          .funnelType("AUTO")
          .startTime(now)
          .endTime(now)
          .build();

      assertThat(dto.getId()).isEqualTo(1L);
      assertThat(dto.getProjectId()).isEqualTo("p");
      assertThat(dto.getName()).isEqualTo("n");
      assertThat(dto.getDescription()).isEqualTo("d");
      assertThat(dto.getStepsJson()).isEmpty();
      assertThat(dto.getWindowSeconds()).isEqualTo(60L);
      assertThat(dto.getMode()).isEqualTo("UNIQUE_USERS");
      assertThat(dto.getDateRangeDays()).isEqualTo(7);
      assertThat(dto.getFiltersJson()).isEmpty();
      assertThat(dto.getCreatedAt()).isEqualTo(now);
      assertThat(dto.getUpdatedAt()).isEqualTo(now);
      assertThat(dto.getCreatedBy()).isEqualTo("u");
      assertThat(dto.getStatus()).isEqualTo("ACTIVE");
      assertThat(dto.getFunnelType()).isEqualTo("AUTO");
      assertThat(dto.getStartTime()).isEqualTo(now);
      assertThat(dto.getEndTime()).isEqualTo(now);
    }
  }

  @Nested
  class UpdateFunnelDefinitionRequestModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      UpdateFunnelDefinitionRequest req = UpdateFunnelDefinitionRequest.builder()
          .name("n")
          .description("d")
          .funnelType(FunnelType.ONCE)
          .stepOrderType(StepOrderType.UNORDERED)
          .steps(List.of())
          .filters(List.of())
          .windowSeconds(60L)
          .mode(FunnelMode.SESSIONS)
          .dateRangeDays(3)
          .startTime(now)
          .endTime(now)
          .expiry(now)
          .tags(List.of())
          .build();

      assertThat(req.getName()).isEqualTo("n");
      assertThat(req.getDescription()).isEqualTo("d");
      assertThat(req.getFunnelType()).isEqualTo(FunnelType.ONCE);
      assertThat(req.getStepOrderType()).isEqualTo(StepOrderType.UNORDERED);
      assertThat(req.getSteps()).isEmpty();
      assertThat(req.getFilters()).isEmpty();
      assertThat(req.getWindowSeconds()).isEqualTo(60L);
      assertThat(req.getMode()).isEqualTo(FunnelMode.SESSIONS);
      assertThat(req.getDateRangeDays()).isEqualTo(3);
      assertThat(req.getStartTime()).isEqualTo(now);
      assertThat(req.getEndTime()).isEqualTo(now);
      assertThat(req.getExpiry()).isEqualTo(now);
      assertThat(req.getTags()).isEmpty();
    }

    @Test
    void shouldHaveDefaults() {
      UpdateFunnelDefinitionRequest req = UpdateFunnelDefinitionRequest.builder().build();
      assertThat(req.getMode()).isEqualTo(FunnelMode.UNIQUE_USERS);
      assertThat(req.getDateRangeDays()).isEqualTo(7);
    }
  }

  @Nested
  class CreateJourneyRequestModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      CreateJourneyRequest req = CreateJourneyRequest.builder()
          .name("j")
          .description("d")
          .anchorEvent("evt")
          .direction(JourneyDirection.END)
          .depth(3)
          .mode(FunnelMode.SESSIONS)
          .filters(List.of())
          .journeyType(FunnelType.ONCE)
          .startTime(now)
          .endTime(now)
          .expiry(now)
          .dateRangeDays(10)
          .tags(List.of("t"))
          .build();

      assertThat(req.getName()).isEqualTo("j");
      assertThat(req.getDescription()).isEqualTo("d");
      assertThat(req.getAnchorEvent()).isEqualTo("evt");
      assertThat(req.getDirection()).isEqualTo(JourneyDirection.END);
      assertThat(req.getDepth()).isEqualTo(3);
      assertThat(req.getMode()).isEqualTo(FunnelMode.SESSIONS);
      assertThat(req.getFilters()).isEmpty();
      assertThat(req.getJourneyType()).isEqualTo(FunnelType.ONCE);
      assertThat(req.getStartTime()).isEqualTo(now);
      assertThat(req.getEndTime()).isEqualTo(now);
      assertThat(req.getExpiry()).isEqualTo(now);
      assertThat(req.getDateRangeDays()).isEqualTo(10);
      assertThat(req.getTags()).containsExactly("t");
    }

    @Test
    void shouldUseDefaultValues() {
      CreateJourneyRequest req = CreateJourneyRequest.builder().name("n").anchorEvent("a").build();
      assertThat(req.getDirection()).isEqualTo(JourneyDirection.START);
      assertThat(req.getDepth()).isEqualTo(5);
      assertThat(req.getMode()).isEqualTo(FunnelMode.UNIQUE_USERS);
      assertThat(req.getJourneyType()).isEqualTo(FunnelType.AUTO);
      assertThat(req.getDateRangeDays()).isEqualTo(7);
    }
  }

  @Nested
  class JourneyListQueryParamsModel {

    @Test
    void shouldSetAndReadAllFields() {
      JourneyListQueryParams p = new JourneyListQueryParams();
      p.setStatus(List.of("ACTIVE"));
      p.setJourneyType("ONCE");
      p.setSearch("s");
      p.setSearchMode("fts");
      p.setUpdatedAfter("a");
      p.setUpdatedBefore("b");
      p.setCreatedBy("u");
      p.setPage(2);
      p.setPageSize(20);
      p.setLimit(10);
      p.setOffset(5);

      assertThat(p.getStatus()).containsExactly("ACTIVE");
      assertThat(p.getJourneyType()).isEqualTo("ONCE");
      assertThat(p.getSearch()).isEqualTo("s");
      assertThat(p.getSearchMode()).isEqualTo("fts");
      assertThat(p.getUpdatedAfter()).isEqualTo("a");
      assertThat(p.getUpdatedBefore()).isEqualTo("b");
      assertThat(p.getCreatedBy()).isEqualTo("u");
      assertThat(p.getPage()).isEqualTo(2);
      assertThat(p.getPageSize()).isEqualTo(20);
      assertThat(p.getLimit()).isEqualTo(10);
      assertThat(p.getOffset()).isEqualTo(5);
      assertThat(p.toString()).isNotNull();
    }
  }

  @Nested
  class JourneyListResponseModel {

    @Test
    void shouldBuildAndReadAllFields() {
      JourneyResponse item = JourneyResponse.builder().id(1L).build();
      ListFilterOptions opts = ListFilterOptions.builder().creators(List.of()).tags(List.of()).build();
      JourneyListResponse resp = JourneyListResponse.builder()
          .items(List.of(item))
          .totalCount(1L)
          .page(1)
          .pageSize(10)
          .totalPages(1)
          .filterOptions(opts)
          .build();

      assertThat(resp.getItems()).containsExactly(item);
      assertThat(resp.getTotalCount()).isEqualTo(1L);
      assertThat(resp.getPage()).isEqualTo(1);
      assertThat(resp.getPageSize()).isEqualTo(10);
      assertThat(resp.getTotalPages()).isEqualTo(1);
      assertThat(resp.getFilterOptions()).isEqualTo(opts);
    }
  }

  @Nested
  class JourneyResponseModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      JourneyResultsResponse results = JourneyResultsResponse.builder()
          .nodes(List.of())
          .links(List.of())
          .build();
      JourneyResponse r = JourneyResponse.builder()
          .id(1L)
          .projectId("p")
          .name("n")
          .description("d")
          .status(AnalysisComputedStatus.ACTIVE)
          .anchorEvent("a")
          .direction(JourneyDirection.START)
          .depth(5)
          .mode(FunnelMode.UNIQUE_USERS)
          .filters(List.of())
          .journeyType(FunnelType.AUTO)
          .startTime(now)
          .endTime(now)
          .expiry(now)
          .dateRangeDays(7)
          .createdAt(now)
          .updatedAt(now)
          .createdBy("u")
          .journeyResults(results)
          .tags(List.of("t"))
          .build();

      assertThat(r.getId()).isEqualTo(1L);
      assertThat(r.getProjectId()).isEqualTo("p");
      assertThat(r.getName()).isEqualTo("n");
      assertThat(r.getDescription()).isEqualTo("d");
      assertThat(r.getStatus()).isEqualTo(AnalysisComputedStatus.ACTIVE);
      assertThat(r.getAnchorEvent()).isEqualTo("a");
      assertThat(r.getDirection()).isEqualTo(JourneyDirection.START);
      assertThat(r.getDepth()).isEqualTo(5);
      assertThat(r.getMode()).isEqualTo(FunnelMode.UNIQUE_USERS);
      assertThat(r.getFilters()).isEmpty();
      assertThat(r.getJourneyType()).isEqualTo(FunnelType.AUTO);
      assertThat(r.getStartTime()).isEqualTo(now);
      assertThat(r.getEndTime()).isEqualTo(now);
      assertThat(r.getExpiry()).isEqualTo(now);
      assertThat(r.getDateRangeDays()).isEqualTo(7);
      assertThat(r.getCreatedAt()).isEqualTo(now);
      assertThat(r.getUpdatedAt()).isEqualTo(now);
      assertThat(r.getCreatedBy()).isEqualTo("u");
      assertThat(r.getJourneyResults()).isEqualTo(results);
      assertThat(r.getTags()).containsExactly("t");
    }
  }

  @Nested
  class JourneyResultsResponseModel {

    @Test
    void shouldBuildAndReadAllFields() {
      JourneySankeyNode node = JourneySankeyNode.builder().name("n").build();
      JourneySankeyLink link = JourneySankeyLink.builder().source("a").target("b").value(10L).build();
      JourneyResultsResponse r = JourneyResultsResponse.builder()
          .nodes(List.of(node)).links(List.of(link)).build();
      assertThat(r.getNodes()).containsExactly(node);
      assertThat(r.getLinks()).containsExactly(link);
      assertThat(node.getName()).isEqualTo("n");
      assertThat(link.getSource()).isEqualTo("a");
      assertThat(link.getTarget()).isEqualTo("b");
      assertThat(link.getValue()).isEqualTo(10L);
    }
  }

  @Nested
  class UpdateJourneyRequestModel {

    @Test
    void shouldBuildAndReadAllFields() {
      Instant now = Instant.now();
      UpdateJourneyRequest r = UpdateJourneyRequest.builder()
          .name("n").description("d").anchorEvent("a")
          .direction(JourneyDirection.END).depth(3)
          .mode(FunnelMode.SESSIONS).filters(List.of())
          .journeyType(FunnelType.ONCE)
          .startTime(now).endTime(now).expiry(now)
          .dateRangeDays(14).tags(List.of())
          .build();

      assertThat(r.getName()).isEqualTo("n");
      assertThat(r.getDescription()).isEqualTo("d");
      assertThat(r.getAnchorEvent()).isEqualTo("a");
      assertThat(r.getDirection()).isEqualTo(JourneyDirection.END);
      assertThat(r.getDepth()).isEqualTo(3);
      assertThat(r.getMode()).isEqualTo(FunnelMode.SESSIONS);
      assertThat(r.getFilters()).isEmpty();
      assertThat(r.getJourneyType()).isEqualTo(FunnelType.ONCE);
      assertThat(r.getStartTime()).isEqualTo(now);
      assertThat(r.getEndTime()).isEqualTo(now);
      assertThat(r.getExpiry()).isEqualTo(now);
      assertThat(r.getDateRangeDays()).isEqualTo(14);
      assertThat(r.getTags()).isEmpty();
    }

    @Test
    void shouldDefaultMode() {
      UpdateJourneyRequest r = UpdateJourneyRequest.builder().build();
      assertThat(r.getMode()).isEqualTo(FunnelMode.UNIQUE_USERS);
    }
  }

  @Nested
  class FunnelEventsResponseModel {

    @Test
    void shouldBuildAndReadFields() {
      FunnelEventsResponse r = FunnelEventsResponse.builder().events(List.of("a", "b")).build();
      assertThat(r.getEvents()).containsExactly("a", "b");
      FunnelEventsResponse same = new FunnelEventsResponse(List.of("a", "b"));
      assertThat(r).isEqualTo(same).hasSameHashCodeAs(same);
    }
  }

  @Nested
  class FunnelFilterKeysResponseModel {

    @Test
    void shouldBuildAndReadFields() {
      FunnelFilterKeysResponse r = FunnelFilterKeysResponse.builder().filters(List.of("k")).build();
      assertThat(r.getFilters()).containsExactly("k");
      FunnelFilterKeysResponse same = new FunnelFilterKeysResponse(List.of("k"));
      assertThat(r).isEqualTo(same).hasSameHashCodeAs(same);
    }
  }

  @Nested
  class FunnelFilterValuesResponseModel {

    @Test
    void shouldBuildAndReadFields() {
      FunnelFilterValuesResponse r = FunnelFilterValuesResponse.builder().values(List.of("v")).build();
      assertThat(r.getValues()).containsExactly("v");
    }
  }

  @Nested
  class FunnelJourneyTagsListResponseModel {

    @Test
    void shouldBuildAndReadFields() {
      FunnelJourneyTagsListResponse r = FunnelJourneyTagsListResponse.builder().tags(List.of("t")).build();
      assertThat(r.getTags()).containsExactly("t");
    }
  }

  @Nested
  class ListFilterOptionsModel {

    @Test
    void shouldBuildAndReadFields() {
      ListFilterOptions o = ListFilterOptions.builder()
          .creators(List.of("u1")).tags(List.of("t1")).build();
      assertThat(o.getCreators()).containsExactly("u1");
      assertThat(o.getTags()).containsExactly("t1");
      ListFilterOptions noargs = new ListFilterOptions();
      noargs.setCreators(List.of());
      noargs.setTags(List.of());
      assertThat(noargs.getCreators()).isEmpty();
      assertThat(noargs.getTags()).isEmpty();
    }
  }

  @Nested
  class ReplaceEntityTagsRequestModel {

    @Test
    void shouldBuildAndReadFields() {
      ReplaceEntityTagsRequest r = ReplaceEntityTagsRequest.builder().tags(List.of("a", "b")).build();
      assertThat(r.getTags()).containsExactly("a", "b");
      ReplaceEntityTagsRequest noargs = new ReplaceEntityTagsRequest();
      noargs.setTags(List.of());
      assertThat(noargs.getTags()).isEmpty();
    }
  }
}

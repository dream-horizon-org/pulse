package org.dreamhorizon.pulseserver.service.webvitals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.webvitals.WebVitalsDao;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalByScreenRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalSummaryRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalTrendRow;
import org.dreamhorizon.pulseserver.resources.webvitals.ScreenVitalDto;
import org.dreamhorizon.pulseserver.resources.webvitals.TrendPointDto;
import org.dreamhorizon.pulseserver.resources.webvitals.VitalSummaryDto;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsByScreenResponseDto;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsSummaryResponseDto;
import org.dreamhorizon.pulseserver.resources.webvitals.WebVitalsTrendResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebVitalsServiceImpl")
class WebVitalsServiceImplTest {

  @Mock private WebVitalsDao webVitalsDao;

  @InjectMocks private WebVitalsServiceImpl webVitalsService;

  private static final Instant START_TIME = Instant.parse("2026-05-01T00:00:00Z");
  private static final Instant END_TIME = Instant.parse("2026-05-02T00:00:00Z");

  @Nested
  @DisplayName("getSummary")
  class GetSummary {

    @Test
    @DisplayName("should_calculate_goodPct_correctly")
    void shouldCalculateGoodPctCorrectly() {
      WebVitalSummaryRow row =
          WebVitalSummaryRow.builder()
              .vitalName("LCP")
              .p75("2500.0")
              .goodCount("80")
              .needsImprovementCount("15")
              .poorCount("5")
              .totalCount("100")
              .build();

      when(webVitalsDao.getSummary(START_TIME, END_TIME, null))
          .thenReturn(Single.just(List.of(row)));

      WebVitalsSummaryResponseDto response =
          webVitalsService.getSummary(START_TIME, END_TIME, null).blockingGet();

      assertThat(response.getVitals()).hasSize(1);
      VitalSummaryDto vital = response.getVitals().get(0);
      assertThat(vital.getGoodPct()).isEqualTo(80.0);
      assertThat(vital.getNeedsImprovementPct()).isEqualTo(15.0);
      assertThat(vital.getPoorPct()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("should_parse_String_p75_to_Double_safely")
    void shouldParseStringP75ToDoubleSafely() {
      WebVitalSummaryRow row =
          WebVitalSummaryRow.builder()
              .vitalName("LCP")
              .p75("2500.75")
              .goodCount("50")
              .needsImprovementCount("30")
              .poorCount("20")
              .totalCount("100")
              .build();

      when(webVitalsDao.getSummary(START_TIME, END_TIME, null))
          .thenReturn(Single.just(List.of(row)));

      WebVitalsSummaryResponseDto response =
          webVitalsService.getSummary(START_TIME, END_TIME, null).blockingGet();

      assertThat(response.getVitals()).hasSize(1);
      assertThat(response.getVitals().get(0).getP75()).isEqualTo(2500.75);
    }

    @Test
    @DisplayName("should_handle_NaN_from_quantile_on_empty_result")
    void shouldHandleNaNFromQuantileOnEmptyResult() {
      WebVitalSummaryRow row =
          WebVitalSummaryRow.builder()
              .vitalName("INP")
              .p75("NaN")
              .goodCount("0")
              .needsImprovementCount("0")
              .poorCount("0")
              .totalCount("0")
              .build();

      when(webVitalsDao.getSummary(START_TIME, END_TIME, null))
          .thenReturn(Single.just(List.of(row)));

      WebVitalsSummaryResponseDto response =
          webVitalsService.getSummary(START_TIME, END_TIME, null).blockingGet();

      assertThat(response.getVitals()).isEmpty();
    }

    @Test
    @DisplayName("should_omit_vitals_with_zero_samples")
    void shouldOmitVitalsWithZeroSamples() {
      WebVitalSummaryRow validRow =
          WebVitalSummaryRow.builder()
              .vitalName("LCP")
              .p75("2500.0")
              .goodCount("50")
              .needsImprovementCount("30")
              .poorCount("20")
              .totalCount("100")
              .build();

      WebVitalSummaryRow zeroRow =
          WebVitalSummaryRow.builder()
              .vitalName("CLS")
              .p75("0.1")
              .goodCount("0")
              .needsImprovementCount("0")
              .poorCount("0")
              .totalCount("0")
              .build();

      when(webVitalsDao.getSummary(START_TIME, END_TIME, null))
          .thenReturn(Single.just(List.of(validRow, zeroRow)));

      WebVitalsSummaryResponseDto response =
          webVitalsService.getSummary(START_TIME, END_TIME, null).blockingGet();

      assertThat(response.getVitals()).hasSize(1);
      assertThat(response.getVitals().get(0).getName()).isEqualTo("LCP");
    }

    @Test
    @DisplayName("should_handle_zero_totalCount_without_division_by_zero")
    void shouldHandleZeroTotalCountWithoutDivisionByZero() {
      WebVitalSummaryRow row =
          WebVitalSummaryRow.builder()
              .vitalName("LCP")
              .p75("0.0")
              .goodCount("0")
              .needsImprovementCount("0")
              .poorCount("0")
              .totalCount("0")
              .build();

      when(webVitalsDao.getSummary(START_TIME, END_TIME, null))
          .thenReturn(Single.just(List.of(row)));

      WebVitalsSummaryResponseDto response =
          webVitalsService.getSummary(START_TIME, END_TIME, null).blockingGet();

      assertThat(response.getVitals()).isEmpty();
    }

    @Test
    @DisplayName("should_route_to_global_query_when_screenName_null")
    void shouldRouteToGlobalQueryWhenScreenNameNull() {
      when(webVitalsDao.getSummary(START_TIME, END_TIME, null))
          .thenReturn(Single.just(List.of()));

      webVitalsService.getSummary(START_TIME, END_TIME, null).blockingGet();

      org.mockito.Mockito.verify(webVitalsDao).getSummary(START_TIME, END_TIME, null);
    }

    @Test
    @DisplayName("should_route_to_per_screen_query_when_screenName_provided")
    void shouldRouteToPerScreenQueryWhenScreenNameProvided() {
      when(webVitalsDao.getSummary(START_TIME, END_TIME, "Home"))
          .thenReturn(Single.just(List.of()));

      webVitalsService.getSummary(START_TIME, END_TIME, "Home").blockingGet();

      org.mockito.Mockito.verify(webVitalsDao).getSummary(START_TIME, END_TIME, "Home");
    }

    @Test
    @DisplayName("should_handle_NumberFormatException_gracefully")
    void shouldHandleNumberFormatExceptionGracefully() {
      WebVitalSummaryRow validRow =
          WebVitalSummaryRow.builder()
              .vitalName("LCP")
              .p75("2500.0")
              .goodCount("50")
              .needsImprovementCount("30")
              .poorCount("20")
              .totalCount("100")
              .build();

      WebVitalSummaryRow invalidRow =
          WebVitalSummaryRow.builder()
              .vitalName("INP")
              .p75("invalid")
              .goodCount("10")
              .needsImprovementCount("5")
              .poorCount("3")
              .totalCount("18")
              .build();

      when(webVitalsDao.getSummary(START_TIME, END_TIME, null))
          .thenReturn(Single.just(List.of(validRow, invalidRow)));

      WebVitalsSummaryResponseDto response =
          webVitalsService.getSummary(START_TIME, END_TIME, null).blockingGet();

      assertThat(response.getVitals()).hasSize(1);
      assertThat(response.getVitals().get(0).getName()).isEqualTo("LCP");
    }
  }

  @Nested
  @DisplayName("getTrend")
  class GetTrend {

    @Test
    @DisplayName("should_return_trend_points_with_parsed_p75")
    void shouldReturnTrendPointsWithParsedP75() {
      WebVitalTrendRow row1 =
          WebVitalTrendRow.builder().bucket("2026-05-01T00:00:00Z").p75("2500.0").build();
      WebVitalTrendRow row2 =
          WebVitalTrendRow.builder().bucket("2026-05-01T05:00:00Z").p75("2600.5").build();

      when(webVitalsDao.getTrend(START_TIME, END_TIME, "LCP", 60, null))
          .thenReturn(Single.just(List.of(row1, row2)));

      WebVitalsTrendResponseDto response =
          webVitalsService.getTrend(START_TIME, END_TIME, "LCP", 60, null).blockingGet();

      assertThat(response.getPoints()).hasSize(2);
      assertThat(response.getPoints().get(0).getP75()).isEqualTo(2500.0);
      assertThat(response.getPoints().get(1).getP75()).isEqualTo(2600.5);
    }

    @Test
    @DisplayName("should_omit_NaN_trend_points")
    void shouldOmitNaNTrendPoints() {
      WebVitalTrendRow validRow =
          WebVitalTrendRow.builder().bucket("2026-05-01T00:00:00Z").p75("2500.0").build();
      WebVitalTrendRow nanRow =
          WebVitalTrendRow.builder().bucket("2026-05-01T05:00:00Z").p75("NaN").build();

      when(webVitalsDao.getTrend(START_TIME, END_TIME, "LCP", 60, null))
          .thenReturn(Single.just(List.of(validRow, nanRow)));

      WebVitalsTrendResponseDto response =
          webVitalsService.getTrend(START_TIME, END_TIME, "LCP", 60, null).blockingGet();

      assertThat(response.getPoints()).hasSize(1);
      assertThat(response.getPoints().get(0).getBucket()).isEqualTo("2026-05-01T00:00:00Z");
    }

    @Test
    @DisplayName("should_route_with_screenName")
    void shouldRouteWithScreenName() {
      when(webVitalsDao.getTrend(START_TIME, END_TIME, "LCP", 60, "Home"))
          .thenReturn(Single.just(List.of()));

      webVitalsService.getTrend(START_TIME, END_TIME, "LCP", 60, "Home").blockingGet();

      org.mockito.Mockito.verify(webVitalsDao).getTrend(START_TIME, END_TIME, "LCP", 60, "Home");
    }
  }

  @Nested
  @DisplayName("getByScreen")
  class GetByScreen {

    @Test
    @DisplayName("should_return_per_screen_vitals")
    void shouldReturnPerScreenVitals() {
      WebVitalByScreenRow row1 =
          WebVitalByScreenRow.builder()
              .screenName("Home")
              .p75("2500.0")
              .totalCount("100")
              .goodPct("80.0")
              .build();
      WebVitalByScreenRow row2 =
          WebVitalByScreenRow.builder()
              .screenName("Details")
              .p75("3000.0")
              .totalCount("50")
              .goodPct("70.0")
              .build();

      when(webVitalsDao.getByScreen(START_TIME, END_TIME, "LCP"))
          .thenReturn(Single.just(List.of(row1, row2)));

      WebVitalsByScreenResponseDto response =
          webVitalsService.getByScreen(START_TIME, END_TIME, "LCP").blockingGet();

      assertThat(response.getScreens()).hasSize(2);
      assertThat(response.getScreens().get(0).getScreenName()).isEqualTo("Home");
      assertThat(response.getScreens().get(0).getP75()).isEqualTo(2500.0);
      assertThat(response.getScreens().get(0).getTotalCount()).isEqualTo(100L);
      assertThat(response.getScreens().get(0).getGoodPct()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("should_omit_screens_with_invalid_p75")
    void shouldOmitScreensWithInvalidP75() {
      WebVitalByScreenRow validRow =
          WebVitalByScreenRow.builder()
              .screenName("Home")
              .p75("2500.0")
              .totalCount("100")
              .goodPct("80.0")
              .build();
      WebVitalByScreenRow invalidRow =
          WebVitalByScreenRow.builder()
              .screenName("Details")
              .p75("invalid")
              .totalCount("50")
              .goodPct("70.0")
              .build();

      when(webVitalsDao.getByScreen(START_TIME, END_TIME, "LCP"))
          .thenReturn(Single.just(List.of(validRow, invalidRow)));

      WebVitalsByScreenResponseDto response =
          webVitalsService.getByScreen(START_TIME, END_TIME, "LCP").blockingGet();

      assertThat(response.getScreens()).hasSize(1);
      assertThat(response.getScreens().get(0).getScreenName()).isEqualTo("Home");
    }
  }
}
